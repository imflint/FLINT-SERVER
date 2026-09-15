package kr.flint.batch.repository;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import io.hypersistence.tsid.TSID;
import kr.flint.batch.job.TmdbIdLine;
import kr.flint.content.domain.MediaType;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class TmdbCatalogEntryJdbcRepository {

	private final JdbcTemplate jdbcTemplate;
	private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

	@Transactional
	public List<TmdbIdLine> registerExportBatch(
		MediaType mediaType,
		LocalDate exportDate,
		List<TmdbIdLine> lines
	) {
		if (CollectionUtils.isEmpty(lines)) {
			return List.of();
		}

		Map<Long, EntryState> existing = findExisting(mediaType, lines);
		upsertExportEntries(mediaType, exportDate, lines);
		LocalDateTime now = LocalDateTime.now(java.time.Clock.systemUTC());
		return lines.stream()
			.filter(line -> requiresDetail(existing.get(line.id()), now))
			.toList();
	}

	private Map<Long, EntryState> findExisting(MediaType mediaType, List<TmdbIdLine> lines) {
		List<Long> tmdbIds = lines.stream().map(TmdbIdLine::id).distinct().toList();
		MapSqlParameterSource params = new MapSqlParameterSource()
			.addValue("mediaType", mediaType.name())
			.addValue("tmdbIds", tmdbIds);
		return namedParameterJdbcTemplate.query("""
			SELECT tmdb_id, status, next_refresh_at
			FROM tmdb_catalog_entry
			WHERE media_type = :mediaType
			  AND tmdb_id IN (:tmdbIds)
			""", params, resultSet -> {
			Map<Long, EntryState> result = new LinkedHashMap<>();
			while (resultSet.next()) {
				Timestamp nextRefreshAt = resultSet.getTimestamp("next_refresh_at");
				result.put(
					resultSet.getLong("tmdb_id"),
					new EntryState(
						resultSet.getString("status"),
						nextRefreshAt == null ? null : nextRefreshAt.toLocalDateTime()
					)
				);
			}
			return result;
		});
	}

	private void upsertExportEntries(MediaType mediaType, LocalDate exportDate, List<TmdbIdLine> lines) {
		String sql = """
			INSERT INTO tmdb_catalog_entry (
				id, media_type, tmdb_id, status, last_seen_export_date, created_at, updated_at
			) VALUES (?, ?, ?, 'PENDING', ?, UTC_TIMESTAMP(), UTC_TIMESTAMP())
			ON DUPLICATE KEY UPDATE
				last_seen_export_date = VALUES(last_seen_export_date),
				updated_at = UTC_TIMESTAMP()
			""";
		jdbcTemplate.batchUpdate(sql, new ArrayList<>(lines), lines.size(), (statement, line) -> {
			statement.setLong(1, TSID.Factory.getTsid().toLong());
			statement.setString(2, mediaType.name());
			statement.setLong(3, line.id());
			statement.setObject(4, exportDate);
		});
	}

	private boolean requiresDetail(EntryState state, LocalDateTime now) {
		if (state == null || "PENDING".equals(state.status()) || "RETRY".equals(state.status())) {
			return true;
		}
		return "SYNCED".equals(state.status())
			&& (state.nextRefreshAt() == null || !state.nextRefreshAt().isAfter(now));
	}

	private record EntryState(String status, LocalDateTime nextRefreshAt) {
	}
}
