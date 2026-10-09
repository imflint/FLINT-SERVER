package kr.flint.batch.repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import kr.flint.batch.repository.ContentBatchJdbcRepository.ContentIdentity;
import kr.flint.content.domain.ContentTitleNormalizer;
import kr.flint.content.domain.MediaType;
import kr.flint.content.dto.ContentUpsertCommand;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class TmdbContentAdmissionJdbcRepository {

    public static final String WRITE_LOCK = "TMDB_CONTENT_WRITE";
    private static final String CONTENT_COLUMNS = """
        id, tmdb_id, media_type, title_ko, title_en, `year`, HEX(title_dedup_key) AS title_key
        """;

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    public boolean schemaReady() {
        Integer ready = jdbcTemplate.queryForObject("""
            SELECT
                EXISTS (SELECT 1 FROM information_schema.columns
                        WHERE table_schema = DATABASE() AND table_name = 'content'
                          AND column_name = 'title_dedup_key' AND column_type = 'varbinary(1020)'
                          AND extra = 'STORED GENERATED')
                AND EXISTS (SELECT 1 FROM information_schema.statistics
                            WHERE table_schema = DATABASE() AND table_name = 'content'
                              AND index_name = 'idx_content_title_dedup'
                            GROUP BY index_name
                            HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index) = 'media_type,year,title_dedup_key'
                              AND MAX(non_unique) = 1)
                AND (SELECT COUNT(*) FROM information_schema.tables
                     WHERE table_schema = DATABASE() AND table_name IN ('content', 'tmdb_sync_lock')
                       AND engine = 'InnoDB') = 2
            """, Integer.class);
        if (ready == null || ready != 1) {
            return false;
        }
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM tmdb_sync_lock WHERE lock_name = ?)", Boolean.class, WRITE_LOCK
        ));
    }

    public void lockWrites() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("TMDB admission requires a transaction");
        }
        List<String> locks = jdbcTemplate.queryForList(
            "SELECT lock_name FROM tmdb_sync_lock WHERE lock_name = ? FOR UPDATE", String.class, WRITE_LOCK
        );
        if (locks.isEmpty()) {
            throw new IllegalStateException("TMDB admission DDL has not been applied");
        }
    }

    public Map<ContentIdentity, ExistingContent> findExisting(List<ContentUpsertCommand> commands) {
        if (commands.isEmpty()) {
            return Map.of();
        }
        var params = new MapSqlParameterSource()
            .addValue("tmdbIds", commands.stream().map(ContentUpsertCommand::tmdbId).distinct().toList())
            .addValue("mediaTypes", commands.stream().map(c -> c.mediaType().name()).distinct().toList());
        return indexByIdentity(namedJdbcTemplate.query(
            "SELECT " + CONTENT_COLUMNS + " FROM content WHERE tmdb_id IN (:tmdbIds)"
                + " AND media_type IN (:mediaTypes) ORDER BY id FOR UPDATE",
            params, (rs, row) -> existingContent(rs)
        ));
    }

    // MySQL LOWER/TRIM을 재사용해 Java와 DB의 대소문자·공백 판정 차이를 없앤다.
    public Map<ContentIdentity, TitleKey> candidateKeys(List<ContentUpsertCommand> commands) {
        if (commands.isEmpty()) {
            return Map.of();
        }
        List<Object> parameters = new ArrayList<>();
        List<String> selects = new ArrayList<>();
        for (int index = 0; index < commands.size(); index++) {
            selects.add("SELECT " + index
                + " AS ordinal, HEX(CAST(LOWER(TRIM(CONVERT(? USING utf8mb4))) AS BINARY)) AS title_key");
            ContentUpsertCommand command = commands.get(index);
            parameters.add(ContentTitleNormalizer.displayTitle(command.titleKo(), command.titleEn()));
        }
        Map<ContentIdentity, TitleKey> result = new LinkedHashMap<>();
        jdbcTemplate.query(String.join(" UNION ALL ", selects), rs -> {
            ContentUpsertCommand command = commands.get(rs.getInt("ordinal"));
            String titleKey = rs.getString("title_key");
            if (command.year() > 0 && titleKey != null && !titleKey.isEmpty()) {
                result.put(identity(command), new TitleKey(command.mediaType(), command.year(), titleKey));
            }
        }, parameters.toArray());
        return result;
    }

    public List<ExistingContent> findTitleOwners(List<TitleKey> keys) {
        List<TitleKey> distinct = keys.stream().distinct().toList();
        if (distinct.isEmpty()) {
            return List.of();
        }
        List<Object> parameters = new ArrayList<>();
        for (TitleKey key : distinct) {
            parameters.add(key.mediaType().name());
            parameters.add(key.year());
            parameters.add(key.binaryTitle());
        }
        String conditions = distinct.stream()
            .map(key -> "(media_type = ? AND `year` = ? AND title_dedup_key = UNHEX(?))")
            .collect(Collectors.joining(" OR "));
        return jdbcTemplate.query("SELECT " + CONTENT_COLUMNS + " FROM content WHERE " + conditions
            + " ORDER BY id FOR UPDATE", (rs, row) -> existingContent(rs), parameters.toArray());
    }

    public List<PromotionCandidate> findPromotionCandidates(long afterId, int limit) {
        return jdbcTemplate.query("""
            SELECT c.id, c.tmdb_id, c.media_type, c.`year`, registry.title_ko, registry.title_en
            FROM content c
            JOIN tmdb_catalog_entry registry
              ON registry.tmdb_id = c.tmdb_id AND registry.media_type = c.media_type
            WHERE c.id > ? AND registry.status = 'SYNCED'
              AND (NULLIF(TRIM(registry.title_ko), '') IS NOT NULL
                   OR NULLIF(TRIM(registry.title_en), '') IS NOT NULL)
            ORDER BY c.id LIMIT ? FOR UPDATE
            """, (rs, row) -> new PromotionCandidate(rs.getLong("id"), ContentUpsertCommand.localized(
            rs.getLong("tmdb_id"), MediaType.valueOf(rs.getString("media_type")),
            rs.getString("title_ko"), rs.getString("title_en"), rs.getInt("year"),
            null, null, null, List.of()
        )), afterId, limit);
    }

    private Map<ContentIdentity, ExistingContent> indexByIdentity(List<ExistingContent> contents) {
        Map<ContentIdentity, ExistingContent> result = new LinkedHashMap<>();
        contents.forEach(content -> result.put(content.identity(), content));
        return result;
    }

    private ExistingContent existingContent(java.sql.ResultSet rs) throws java.sql.SQLException {
        MediaType type = MediaType.valueOf(rs.getString("media_type"));
        int year = rs.getInt("year");
        String key = rs.getString("title_key");
        return new ExistingContent(rs.getLong("id"), new ContentIdentity(rs.getLong("tmdb_id"), type),
            rs.getString("title_ko"), rs.getString("title_en"), year,
            year > 0 && key != null && !key.isEmpty() ? new TitleKey(type, year, key) : null);
    }

    public static ContentIdentity identity(ContentUpsertCommand command) {
        return new ContentIdentity(command.tmdbId(), command.mediaType());
    }

    public record TitleKey(MediaType mediaType, int year, String binaryTitle) {
    }

    public record ExistingContent(long id, ContentIdentity identity, String titleKo, String titleEn,
                                  int year, TitleKey titleKey) {
    }

    public record PromotionCandidate(long id, ContentUpsertCommand command) {
    }
}
