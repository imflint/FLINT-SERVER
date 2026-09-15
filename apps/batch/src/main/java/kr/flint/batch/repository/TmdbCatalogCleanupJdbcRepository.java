package kr.flint.batch.repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import io.hypersistence.tsid.TSID;
import kr.flint.batch.sync.TmdbPruneManifest;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class TmdbCatalogCleanupJdbcRepository {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public long countUnclassifiedContents() {
        return jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM content c
            LEFT JOIN tmdb_catalog_entry registry
              ON registry.tmdb_id = c.tmdb_id AND registry.media_type = c.media_type
            WHERE registry.id IS NULL OR registry.status IN ('PENDING', 'RETRY')
            """, Long.class);
    }

    public List<Long> findIneligibleContentIds() {
        return jdbcTemplate.queryForList("""
            SELECT c.id
            FROM content c
            JOIN tmdb_catalog_entry registry
              ON registry.tmdb_id = c.tmdb_id AND registry.media_type = c.media_type
            WHERE registry.status = 'INELIGIBLE_LANGUAGE'
            ORDER BY c.id
            """, Long.class);
    }

    @Transactional
    public TmdbPruneManifest createManifest(List<Long> contentIds, String hash) {
        long manifestId = TSID.Factory.getTsid().toLong();
        jdbcTemplate.update("""
            INSERT INTO tmdb_content_prune_manifest (
                id, status, candidate_count, candidate_hash, processed_count, created_at
            ) VALUES (?, 'PREVIEW', ?, ?, 0, UTC_TIMESTAMP())
            """, manifestId, contentIds.size(), hash);

        jdbcTemplate.batchUpdate("""
            INSERT INTO tmdb_content_prune_candidate (manifest_id, content_id, processed)
            VALUES (?, ?, FALSE)
            """, contentIds, 500, (ps, contentId) -> {
            ps.setLong(1, manifestId);
            ps.setLong(2, contentId);
        });

        jdbcTemplate.update("""
            INSERT IGNORE INTO tmdb_content_prune_collection (manifest_id, collection_id)
            SELECT ?, collection_id
            FROM collection_content
            WHERE content_id IN (
                SELECT content_id FROM tmdb_content_prune_candidate WHERE manifest_id = ?
            )
            """, manifestId, manifestId);
        return findManifest(manifestId).orElseThrow();
    }

    public Optional<TmdbPruneManifest> findManifest(Long manifestId) {
        return jdbcTemplate.query("""
            SELECT id, status, candidate_count, candidate_hash, processed_count, created_at, executed_at
            FROM tmdb_content_prune_manifest
            WHERE id = ?
            """, (rs, rowNum) -> new TmdbPruneManifest(
            rs.getLong("id"),
            rs.getString("status"),
            rs.getLong("candidate_count"),
            rs.getString("candidate_hash"),
            rs.getLong("processed_count"),
            toLocalDateTime(rs.getTimestamp("created_at")),
            toLocalDateTime(rs.getTimestamp("executed_at"))
        ), manifestId).stream().findFirst();
    }

    @Transactional
    public int deleteNextChunk(Long manifestId, int chunkSize) {
        List<Long> contentIds = jdbcTemplate.queryForList("""
            SELECT content_id
            FROM tmdb_content_prune_candidate
            WHERE manifest_id = ? AND processed = FALSE
            ORDER BY content_id
            LIMIT ?
            FOR UPDATE
            """, Long.class, manifestId, chunkSize);
        if (contentIds.isEmpty()) {
            return 0;
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("manifestId", manifestId)
            .addValue("contentIds", contentIds);

			namedParameterJdbcTemplate.update("""
			INSERT IGNORE INTO tmdb_s3_delete_queue (
				id, object_key, object_key_hash, delete_after, status, retry_count, created_at, updated_at
			)
			SELECT UUID_SHORT(), asset.object_key, UNHEX(SHA2(asset.object_key, 256)),
			       DATE_ADD(UTC_TIMESTAMP(), INTERVAL 30 DAY),
			       'PENDING', 0, UTC_TIMESTAMP(), UTC_TIMESTAMP()
            FROM (
                SELECT custom_image AS object_key
                FROM collection_content
                WHERE content_id IN (:contentIds)
                UNION
                SELECT image.image_key AS object_key
                FROM collection_content_images image
                JOIN collection_content cc ON cc.id = image.collection_content_id
                WHERE cc.content_id IN (:contentIds)
            ) asset
            WHERE asset.object_key LIKE 'collection/content/%'
            """, params);

        namedParameterJdbcTemplate.update("""
            DELETE image
            FROM collection_content_images image
            JOIN collection_content cc ON cc.id = image.collection_content_id
            WHERE cc.content_id IN (:contentIds)
            """, params);
        namedParameterJdbcTemplate.update("DELETE FROM collection_content WHERE content_id IN (:contentIds)", params);
        namedParameterJdbcTemplate.update("DELETE FROM content_bookmark WHERE content_id IN (:contentIds)", params);
        namedParameterJdbcTemplate.update("DELETE FROM ott_content WHERE content_id IN (:contentIds)", params);
        namedParameterJdbcTemplate.update("DELETE FROM content_keywords WHERE content_id IN (:contentIds)", params);
        namedParameterJdbcTemplate.update("DELETE FROM content_genre WHERE content_id IN (:contentIds)", params);
        namedParameterJdbcTemplate.update("DELETE FROM content WHERE id IN (:contentIds)", params);
        namedParameterJdbcTemplate.update("""
            UPDATE tmdb_content_prune_candidate
            SET processed = TRUE, processed_at = UTC_TIMESTAMP()
            WHERE manifest_id = :manifestId AND content_id IN (:contentIds)
            """, params);
        jdbcTemplate.update("""
            UPDATE tmdb_content_prune_manifest
            SET processed_count = processed_count + ?, status = 'EXECUTING'
            WHERE id = ?
            """, contentIds.size(), manifestId);
        return contentIds.size();
    }

    @Transactional
    public void finalizeAffectedCollections(Long manifestId) {
        List<Long> collectionIds = findAffectedCollectionIds(manifestId);
        if (!collectionIds.isEmpty()) {
            MapSqlParameterSource params = new MapSqlParameterSource().addValue("collectionIds", collectionIds);
            namedParameterJdbcTemplate.update("""
                UPDATE collection_content cc
                JOIN (
                    SELECT id,
                           ROW_NUMBER() OVER (PARTITION BY collection_id ORDER BY sort_order, id) - 1 AS new_order
                    FROM collection_content
                    WHERE collection_id IN (:collectionIds)
                ) ranked ON ranked.id = cc.id
                SET cc.sort_order = ranked.new_order + 1000000
                """, params);
            namedParameterJdbcTemplate.update("""
                UPDATE collection_content
                SET sort_order = sort_order - 1000000
                WHERE collection_id IN (:collectionIds) AND sort_order >= 1000000
                """, params);
            namedParameterJdbcTemplate.update("""
                UPDATE collection c
                SET c.deleted_at = UTC_TIMESTAMP(), c.updated_at = UTC_TIMESTAMP()
                WHERE c.id IN (:collectionIds)
                  AND c.deleted_at IS NULL
                  AND NOT EXISTS (
                      SELECT 1 FROM collection_content cc WHERE cc.collection_id = c.id
                  )
                """, params);
        }
    }

    @Transactional
    public void completeManifest(Long manifestId) {
        jdbcTemplate.update("""
            UPDATE tmdb_content_prune_manifest
            SET status = 'COMPLETED', executed_at = UTC_TIMESTAMP()
            WHERE id = ? AND status IN ('PREVIEW', 'EXECUTING')
            """, manifestId);
    }

    public List<Long> findAffectedCollectionIds(Long manifestId) {
        return jdbcTemplate.queryForList("""
            SELECT collection_id
            FROM tmdb_content_prune_collection
            WHERE manifest_id = ?
            ORDER BY collection_id
            """, Long.class, manifestId);
    }

    public List<Long> findActiveAffectedCollectionIds(Long manifestId) {
        return jdbcTemplate.queryForList("""
            SELECT affected.collection_id
            FROM tmdb_content_prune_collection affected
            JOIN collection c ON c.id = affected.collection_id
            WHERE affected.manifest_id = ?
              AND c.deleted_at IS NULL
              AND EXISTS (SELECT 1 FROM collection_content cc WHERE cc.collection_id = c.id)
            ORDER BY affected.collection_id
            """, Long.class, manifestId);
    }

    @Transactional
    public int promoteLocalizedTitlesForEligibleContents() {
        return jdbcTemplate.update("""
            UPDATE content c
            JOIN tmdb_catalog_entry registry
              ON registry.tmdb_id = c.tmdb_id AND registry.media_type = c.media_type
            SET c.title_ko = registry.title_ko,
                c.title_en = registry.title_en,
                c.normalized_title_ko = registry.normalized_title_ko,
                c.normalized_title_en = registry.normalized_title_en,
                c.search_title = registry.search_title,
                c.title = COALESCE(NULLIF(registry.title_ko, ''), NULLIF(registry.title_en, '')),
                c.updated_at = UTC_TIMESTAMP()
            WHERE registry.status = 'SYNCED'
              AND (NULLIF(registry.title_ko, '') IS NOT NULL OR NULLIF(registry.title_en, '') IS NOT NULL)
            """);
    }

    private java.time.LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
