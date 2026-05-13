package kr.flint.batch.repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

import io.hypersistence.tsid.TSID;
import kr.flint.content.domain.MediaType;
import kr.flint.content.dto.ContentUpsertCommand;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ContentBatchJdbcRepository {

	private final JdbcTemplate jdbcTemplate;
	private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

	public void upsertAll(List<ContentUpsertCommand> commands) {
		if (CollectionUtils.isEmpty(commands)) {
			return;
		}

		Map<ContentKey, ContentUpsertCommand> latestByKey = new LinkedHashMap<>();
		Map<ContentKey, LinkedHashSet<String>> genreNamesByKey = new LinkedHashMap<>();

		for (ContentUpsertCommand command : commands) {
			if (command == null) {
				continue;
			}
			ContentKey key = contentKey(command);
			latestByKey.put(key, command);
			genreNamesByKey.computeIfAbsent(key, ignored -> new LinkedHashSet<>())
				.addAll(normalizeGenreNames(command.genreNames()));
		}

		if (latestByKey.isEmpty()) {
			return;
		}

		upsertContents(new ArrayList<>(latestByKey.values()));

		Map<ContentKey, Long> contentIds = findContentIds(latestByKey.keySet());
		Set<String> genreNames = collectGenreNames(genreNamesByKey);
		insertMissingGenres(genreNames);
		Map<String, Long> genreIds = findGenreIds(genreNames);
		insertContentGenres(genreNamesByKey, contentIds, genreIds);
	}

	private void upsertContents(List<ContentUpsertCommand> commands) {
		String sql = """
			INSERT INTO content (
				id, tmdb_id, media_type, title, `year`, author, description, poster,
				bookmark_count, created_at, updated_at
			)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
			ON DUPLICATE KEY UPDATE
				updated_at = IF(
					NOT (title <=> VALUES(title))
					OR NOT (`year` <=> VALUES(`year`))
					OR NOT (author <=> VALUES(author))
					OR NOT (description <=> VALUES(description))
					OR NOT (poster <=> VALUES(poster)),
					VALUES(updated_at),
					updated_at
				),
				title = VALUES(title),
				`year` = VALUES(`year`),
				author = VALUES(author),
				description = VALUES(description),
				poster = VALUES(poster)
			""";

		Timestamp timestamp = Timestamp.valueOf(LocalDateTime.now());
		jdbcTemplate.batchUpdate(sql, commands, commands.size(), (ps, command) -> {
			ps.setLong(1, TSID.Factory.getTsid().toLong());
			ps.setLong(2, command.tmdbId());
			ps.setString(3, command.mediaType().name());
			ps.setString(4, command.title());
			ps.setInt(5, command.year());
			ps.setString(6, command.author());
			ps.setString(7, command.description());
			ps.setString(8, command.poster());
			ps.setTimestamp(9, timestamp);
			ps.setTimestamp(10, timestamp);
		});
	}

	private Map<ContentKey, Long> findContentIds(Set<ContentKey> keys) {
		MapSqlParameterSource params = new MapSqlParameterSource()
			.addValue("tmdbIds", keys.stream().map(ContentKey::tmdbId).toList())
			.addValue("mediaTypes", keys.stream().map(key -> key.mediaType().name()).distinct().toList());

		String sql = """
			SELECT id, tmdb_id, media_type
			FROM content
			WHERE tmdb_id IN (:tmdbIds)
				AND media_type IN (:mediaTypes)
			""";

		return namedParameterJdbcTemplate.query(sql, params, rs -> {
			Map<ContentKey, Long> result = new HashMap<>();
			while (rs.next()) {
				ContentKey key = new ContentKey(
					rs.getLong("tmdb_id"),
					MediaType.valueOf(rs.getString("media_type"))
				);
				result.put(key, rs.getLong("id"));
			}
			return result;
		});
	}

	private void insertMissingGenres(Set<String> genreNames) {
		if (genreNames.isEmpty()) {
			return;
		}

		String sql = """
			INSERT IGNORE INTO genre (id, name)
			VALUES (?, ?)
			""";

		List<String> names = new ArrayList<>(genreNames);
		jdbcTemplate.batchUpdate(sql, names, names.size(), (ps, name) -> {
			ps.setLong(1, TSID.Factory.getTsid().toLong());
			ps.setString(2, name);
		});
	}

	private Map<String, Long> findGenreIds(Set<String> genreNames) {
		if (genreNames.isEmpty()) {
			return Map.of();
		}

		String sql = """
			SELECT id, name
			FROM genre
			WHERE name IN (:names)
			""";

		MapSqlParameterSource params = new MapSqlParameterSource()
			.addValue("names", new ArrayList<>(genreNames));

		return namedParameterJdbcTemplate.query(sql, params, rs -> {
			Map<String, Long> result = new HashMap<>();
			while (rs.next()) {
				result.put(rs.getString("name"), rs.getLong("id"));
			}
			return result;
		});
	}

	private void insertContentGenres(
		Map<ContentKey, LinkedHashSet<String>> genreNamesByKey,
		Map<ContentKey, Long> contentIds,
		Map<String, Long> genreIds
	) {
		List<ContentGenreRow> rows = new ArrayList<>();

		for (Map.Entry<ContentKey, LinkedHashSet<String>> entry : genreNamesByKey.entrySet()) {
			Long contentId = contentIds.get(entry.getKey());
			if (contentId == null) {
				throw new IllegalStateException("Content was not found after upsert: " + entry.getKey());
			}
			for (String genreName : entry.getValue()) {
				Long genreId = genreIds.get(genreName);
				if (genreId == null) {
					throw new IllegalStateException("Genre was not found after insert: " + genreName);
				}
				rows.add(new ContentGenreRow(contentId, genreId));
			}
		}

		if (rows.isEmpty()) {
			return;
		}

		String sql = """
			INSERT IGNORE INTO content_genre (id, content_id, genre_id)
			VALUES (?, ?, ?)
			""";

		jdbcTemplate.batchUpdate(sql, rows, rows.size(), (ps, row) -> {
			ps.setLong(1, TSID.Factory.getTsid().toLong());
			ps.setLong(2, row.contentId());
			ps.setLong(3, row.genreId());
		});
	}

	private ContentKey contentKey(ContentUpsertCommand command) {
		return new ContentKey(
			Objects.requireNonNull(command.tmdbId(), "tmdbId must not be null"),
			Objects.requireNonNull(command.mediaType(), "mediaType must not be null")
		);
	}

	private List<String> normalizeGenreNames(List<String> genreNames) {
		if (CollectionUtils.isEmpty(genreNames)) {
			return List.of();
		}
		return genreNames.stream()
			.filter(Objects::nonNull)
			.map(String::trim)
			.filter(name -> !name.isBlank())
			.distinct()
			.toList();
	}

	private Set<String> collectGenreNames(Map<ContentKey, LinkedHashSet<String>> genreNamesByKey) {
		Set<String> genreNames = new LinkedHashSet<>();
		genreNamesByKey.values().forEach(genreNames::addAll);
		return genreNames;
	}

	private record ContentKey(Long tmdbId, MediaType mediaType) {
	}

	private record ContentGenreRow(Long contentId, Long genreId) {
	}
}
