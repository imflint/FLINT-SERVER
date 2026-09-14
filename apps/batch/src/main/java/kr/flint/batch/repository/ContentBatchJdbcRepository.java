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
import kr.flint.content.domain.ContentTitleNormalizer;
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

		upsertCatalogEntries(commands);
		upsertClassified(commands);
	}

	public void classifyAll(List<ContentUpsertCommand> commands) {
		if (!CollectionUtils.isEmpty(commands)) {
			upsertCatalogEntries(commands);
		}
	}

	public void upsertClassified(List<ContentUpsertCommand> commands) {
		if (CollectionUtils.isEmpty(commands)) {
			return;
		}

		Map<ContentKey, ContentUpsertCommand> latestByKey = new LinkedHashMap<>();
		Map<ContentKey, LinkedHashSet<String>> genreNamesByKey = new LinkedHashMap<>();

		for (ContentUpsertCommand command : commands) {
			if (command == null || !command.syncable()) {
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
		deleteExistingContentGenres(contentIds.values());
		Set<String> genreNames = collectGenreNames(genreNamesByKey);
		insertMissingGenres(genreNames);
		Map<String, Long> genreIds = findGenreIds(genreNames);
		insertContentGenres(genreNamesByKey, contentIds, genreIds);
	}

	public Map<ContentIdentity, Long> findContentIdsFor(List<ContentUpsertCommand> commands) {
		Set<ContentKey> keys = commands.stream()
			.filter(Objects::nonNull)
			.filter(ContentUpsertCommand::syncable)
			.map(this::contentKey)
			.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
		Map<ContentKey, Long> found = findContentIds(keys);
		Map<ContentIdentity, Long> result = new LinkedHashMap<>();
		found.forEach((key, id) -> result.put(new ContentIdentity(key.tmdbId(), key.mediaType()), id));
		return result;
	}

	private void upsertCatalogEntries(List<ContentUpsertCommand> commands) {
		List<ContentUpsertCommand> classified = commands.stream()
			.filter(Objects::nonNull)
			.toList();
		if (classified.isEmpty()) {
			return;
		}

		String sql = """
			INSERT INTO tmdb_catalog_entry (
				id, media_type, tmdb_id, status,
				title_ko, title_en, normalized_title_ko, normalized_title_en, search_title,
				last_synced_at, next_refresh_at,
				error_message, created_at, updated_at
			) VALUES (
				?, ?, ?, ?, ?, ?, ?, ?, ?,
				IF(? = 'SYNCED', UTC_TIMESTAMP(), NULL),
				IF(? = 'SYNCED', DATE_ADD(UTC_TIMESTAMP(), INTERVAL 30 DAY), NULL),
				?, UTC_TIMESTAMP(), UTC_TIMESTAMP()
			)
			ON DUPLICATE KEY UPDATE
				status = VALUES(status),
				title_ko = IF(VALUES(status) = 'RETRY', title_ko, VALUES(title_ko)),
				title_en = IF(VALUES(status) = 'RETRY', title_en, VALUES(title_en)),
				normalized_title_ko = IF(VALUES(status) = 'RETRY', normalized_title_ko, VALUES(normalized_title_ko)),
				normalized_title_en = IF(VALUES(status) = 'RETRY', normalized_title_en, VALUES(normalized_title_en)),
				search_title = IF(VALUES(status) = 'RETRY', search_title, VALUES(search_title)),
				last_synced_at = IF(VALUES(status) = 'SYNCED', VALUES(last_synced_at), last_synced_at),
				next_refresh_at = IF(VALUES(status) = 'SYNCED', VALUES(next_refresh_at), next_refresh_at),
				error_message = VALUES(error_message),
				updated_at = UTC_TIMESTAMP()
			""";
		jdbcTemplate.batchUpdate(sql, classified, classified.size(), (ps, command) -> {
			ps.setLong(1, TSID.Factory.getTsid().toLong());
			ps.setString(2, command.mediaType().name());
			ps.setLong(3, command.tmdbId());
			ps.setString(4, command.catalogStatus().name());
			ps.setString(5, command.titleKo());
			ps.setString(6, command.titleEn());
			ps.setString(7, ContentTitleNormalizer.normalizeNullable(command.titleKo()));
			ps.setString(8, ContentTitleNormalizer.normalizeNullable(command.titleEn()));
			ps.setString(9, ContentTitleNormalizer.buildSearchTitle(command.titleKo(), command.titleEn()));
			ps.setString(10, command.catalogStatus().name());
			ps.setString(11, command.catalogStatus().name());
			ps.setString(12, command.errorMessage());
		});
	}

	private void upsertContents(List<ContentUpsertCommand> commands) {
		String sql = """
			INSERT INTO content (
				id, tmdb_id, media_type, title, title_ko, title_en,
				normalized_title_ko, normalized_title_en, search_title,
				`year`, author, description, poster,
				bookmark_count, created_at, updated_at
			)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
			ON DUPLICATE KEY UPDATE
				updated_at = IF(
					NOT (title <=> VALUES(title))
					OR NOT (title_ko <=> VALUES(title_ko))
					OR NOT (title_en <=> VALUES(title_en))
					OR NOT (`year` <=> VALUES(`year`))
					OR NOT (author <=> VALUES(author))
					OR NOT (description <=> VALUES(description))
					OR NOT (poster <=> VALUES(poster)),
					VALUES(updated_at),
					updated_at
				),
				title = VALUES(title),
				title_ko = VALUES(title_ko),
				title_en = VALUES(title_en),
				normalized_title_ko = VALUES(normalized_title_ko),
				normalized_title_en = VALUES(normalized_title_en),
				search_title = VALUES(search_title),
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
			ps.setString(4, ContentTitleNormalizer.displayTitle(command.titleKo(), command.titleEn()));
			ps.setString(5, command.titleKo());
			ps.setString(6, command.titleEn());
			ps.setString(7, ContentTitleNormalizer.normalizeNullable(command.titleKo()));
			ps.setString(8, ContentTitleNormalizer.normalizeNullable(command.titleEn()));
			ps.setString(9, ContentTitleNormalizer.buildSearchTitle(command.titleKo(), command.titleEn()));
			ps.setInt(10, command.year());
			ps.setString(11, command.author());
			ps.setString(12, command.description());
			ps.setString(13, command.poster());
			ps.setTimestamp(14, timestamp);
			ps.setTimestamp(15, timestamp);
		});
	}

	private void deleteExistingContentGenres(java.util.Collection<Long> contentIds) {
		if (contentIds.isEmpty()) {
			return;
		}
		MapSqlParameterSource params = new MapSqlParameterSource()
			.addValue("contentIds", new ArrayList<>(contentIds));
		namedParameterJdbcTemplate.update(
			"DELETE FROM content_genre WHERE content_id IN (:contentIds)",
			params
		);
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

	public record ContentIdentity(Long tmdbId, MediaType mediaType) {
	}

	private record ContentGenreRow(Long contentId, Long genreId) {
	}
}
