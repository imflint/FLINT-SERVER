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
import kr.flint.content.domain.GenreCode;
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

		List<ContentUpsertCommand> admitted = validateGenres(commands);
		upsertCatalogEntries(admitted);
		upsertClassified(admitted);
	}

	public void classifyAll(List<ContentUpsertCommand> commands) {
		if (!CollectionUtils.isEmpty(commands)) {
			upsertCatalogEntries(commands);
		}
	}

	public void upsertClassified(List<ContentUpsertCommand> commands) {
		upsertClassified(commands, Set.of());
	}

	public void upsertClassified(List<ContentUpsertCommand> commands, Set<ContentIdentity> preserveTitles) {
		if (CollectionUtils.isEmpty(commands)) {
			return;
		}

		Map<ContentKey, ContentUpsertCommand> latestByKey = new LinkedHashMap<>();
		Map<ContentKey, LinkedHashSet<Long>> genresByKey = new LinkedHashMap<>();
		GenreMappings mappings = loadGenreMappings();

		for (ContentUpsertCommand command : commands) {
			if (command == null || !command.syncable()) {
				continue;
			}
			ContentKey key = contentKey(command);
			latestByKey.put(key, command);
			genresByKey.computeIfAbsent(key, ignored -> new LinkedHashSet<>())
				.addAll(resolveGenreIds(command, mappings));
		}

		if (latestByKey.isEmpty()) {
			return;
		}

		List<ContentUpsertCommand> accepted = new ArrayList<>(latestByKey.values());
		upsertContents(accepted.stream().filter(c -> !preserveTitles.contains(identity(c))).toList());
		updateMetadataWithoutTitles(accepted.stream().filter(c -> preserveTitles.contains(identity(c))).toList());

		Map<ContentKey, Long> contentIds = findContentIds(latestByKey.keySet());
		deleteExistingContentGenres(contentIds.values());
		insertContentGenres(genresByKey, contentIds);
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

    public void restoreRegistryTitles(Set<ContentIdentity> identities) {
        if (identities.isEmpty()) {
            return;
        }
        List<Object> params = new ArrayList<>();
        List<String> conditions = new ArrayList<>();
        for (ContentIdentity identity : identities) {
            conditions.add("(c.tmdb_id = ? AND c.media_type = ?)");
            params.add(identity.tmdbId());
            params.add(identity.mediaType().name());
        }
        jdbcTemplate.update("""
            UPDATE tmdb_catalog_entry registry
            JOIN content c ON c.tmdb_id = registry.tmdb_id AND c.media_type = registry.media_type
            SET registry.title_ko = c.title_ko, registry.title_en = c.title_en,
                registry.normalized_title_ko = c.normalized_title_ko,
                registry.normalized_title_en = c.normalized_title_en, registry.search_title = c.search_title
            WHERE
            """ + String.join(" OR ", conditions), params.toArray());
    }

    public void promoteTitles(List<ContentUpsertCommand> commands) {
        jdbcTemplate.batchUpdate("""
            UPDATE content SET title = ?, title_ko = ?, title_en = ?, normalized_title_ko = ?,
                normalized_title_en = ?, search_title = ?, updated_at = UTC_TIMESTAMP()
            WHERE tmdb_id = ? AND media_type = ?
            """, commands, Math.max(1, commands.size()), (ps, command) -> {
            ps.setString(1, ContentTitleNormalizer.displayTitle(command.titleKo(), command.titleEn()));
            ps.setString(2, command.titleKo());
            ps.setString(3, command.titleEn());
            ps.setString(4, ContentTitleNormalizer.normalizeNullable(command.titleKo()));
            ps.setString(5, ContentTitleNormalizer.normalizeNullable(command.titleEn()));
            ps.setString(6, ContentTitleNormalizer.buildSearchTitle(command.titleKo(), command.titleEn()));
            ps.setLong(7, command.tmdbId());
            ps.setString(8, command.mediaType().name());
        });
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
				IF(? IN ('SYNCED', 'DUPLICATE_TITLE'), DATE_ADD(UTC_TIMESTAMP(), INTERVAL 30 DAY), NULL),
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
				next_refresh_at = IF(VALUES(status) IN ('SYNCED', 'DUPLICATE_TITLE'), VALUES(next_refresh_at), next_refresh_at),
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
		if (commands.isEmpty()) {
			return;
		}
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

    private void updateMetadataWithoutTitles(List<ContentUpsertCommand> commands) {
        if (commands.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate("""
            UPDATE content SET updated_at = IF(
                NOT (`year` <=> ?) OR NOT (author <=> ?) OR NOT (description <=> ?) OR NOT (poster <=> ?),
                UTC_TIMESTAMP(), updated_at),
                `year` = ?, author = ?, description = ?, poster = ?
            WHERE tmdb_id = ? AND media_type = ?
            """, commands, commands.size(), (ps, command) -> {
            ps.setInt(1, command.year());
            ps.setString(2, command.author());
            ps.setString(3, command.description());
            ps.setString(4, command.poster());
            ps.setInt(5, command.year());
            ps.setString(6, command.author());
            ps.setString(7, command.description());
            ps.setString(8, command.poster());
            ps.setLong(9, command.tmdbId());
            ps.setString(10, command.mediaType().name());
        });
    }

    private ContentIdentity identity(ContentUpsertCommand command) {
        return new ContentIdentity(command.tmdbId(), command.mediaType());
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
		if (keys.isEmpty()) {
			return Map.of();
		}
		MapSqlParameterSource params = new MapSqlParameterSource()
			.addValue("tmdbIds", keys.stream().map(ContentKey::tmdbId).toList())
			.addValue("mediaTypes", keys.stream().map(key -> key.mediaType().name()).distinct().toList());

		String sql = """
			SELECT id, tmdb_id, media_type
			FROM content
			WHERE tmdb_id IN (:tmdbIds)
				AND media_type IN (:mediaTypes)
			ORDER BY id FOR UPDATE
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

	private void insertContentGenres(
		Map<ContentKey, LinkedHashSet<Long>> genresByKey,
		Map<ContentKey, Long> contentIds
	) {
		List<ContentGenreRow> rows = new ArrayList<>();

		for (Map.Entry<ContentKey, LinkedHashSet<Long>> entry : genresByKey.entrySet()) {
			Long contentId = contentIds.get(entry.getKey());
			if (contentId == null) {
				throw new IllegalStateException("Content was not found after upsert: " + entry.getKey());
			}
			for (Long genreId : entry.getValue()) {
				rows.add(new ContentGenreRow(contentId, genreId));
			}
		}

		if (rows.isEmpty()) {
			return;
		}

		String sql = """
			INSERT INTO content_genre (id, content_id, genre_id)
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

	public boolean genreSchemaReady() {
		return Integer.valueOf(1).equals(jdbcTemplate.queryForObject("""
			SELECT COUNT(*) FROM information_schema.columns
			WHERE table_schema=DATABASE() AND table_name='genre' AND column_name='code'
			""", Integer.class)) && Integer.valueOf(1).equals(jdbcTemplate.queryForObject("""
			SELECT COUNT(*) FROM information_schema.tables
			WHERE table_schema=DATABASE() AND table_name='tmdb_genre_mapping'
			""", Integer.class)) && Integer.valueOf(24).equals(jdbcTemplate.queryForObject(
			"SELECT IF(COUNT(*)=24 AND COUNT(DISTINCT code)=24,24,0) FROM genre", Integer.class))
			&& jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tmdb_genre_mapping",Integer.class) >= 35;
	}

	public List<ContentUpsertCommand> validateGenres(List<ContentUpsertCommand> commands) {
		GenreMappings mappings = loadGenreMappings();
		return commands.stream().map(command -> {
			if (command == null || !command.syncable()) return command;
			try {
				resolveGenreIds(command, mappings);
				return command;
			} catch (IllegalArgumentException exception) {
				return command.retry(exception.getMessage());
			}
		}).toList();
	}

	private GenreMappings loadGenreMappings() {
		Map<GenreCode, Long> codes = new HashMap<>();
		jdbcTemplate.query("SELECT id, code FROM genre WHERE code IS NOT NULL", rs -> {
			codes.put(GenreCode.valueOf(rs.getString("code")), rs.getLong("id"));
		});
		Map<ExternalGenreKey, Long> external = new HashMap<>();
		jdbcTemplate.query("""
			SELECT m.media_type, m.tmdb_genre_id, m.genre_id FROM tmdb_genre_mapping m
			JOIN genre g ON g.id=m.genre_id WHERE g.code IS NOT NULL
			""", rs -> {
			external.put(new ExternalGenreKey(MediaType.valueOf(rs.getString("media_type")),
				rs.getLong("tmdb_genre_id")), rs.getLong("genre_id"));
		});
		return new GenreMappings(codes, external);
	}

	private Set<Long> resolveGenreIds(ContentUpsertCommand command, GenreMappings mappings) {
		Set<Long> ids = new LinkedHashSet<>();
		for (Long externalId : command.tmdbGenreIds()) {
			Long id = mappings.external().get(new ExternalGenreKey(command.mediaType(), externalId));
			if (id == null) throw new IllegalArgumentException("Unmapped TMDB genre: " + command.mediaType() + ":" + externalId);
			ids.add(id);
		}
		for (String name : command.genreNames()) {
			GenreCode code = GenreCode.find(name).orElseThrow(() -> new IllegalArgumentException("Unregistered genre alias"));
			Long id = mappings.codes().get(code);
			if (id == null) throw new IllegalArgumentException("Missing canonical genre: " + code);
			ids.add(id);
		}
		return ids;
	}

	private record ExternalGenreKey(MediaType mediaType, Long id) { }
	private record GenreMappings(Map<GenreCode, Long> codes, Map<ExternalGenreKey, Long> external) { }

	private record ContentKey(Long tmdbId, MediaType mediaType) {
	}

	public record ContentIdentity(Long tmdbId, MediaType mediaType) {
	}

	private record ContentGenreRow(Long contentId, Long genreId) {
	}
}
