-- Flint platform API + TMDB catalog compatibility DDL (MySQL 8.0)
-- Apply before deploying the integrated API. This file does not delete content.
-- Take a schema backup and execute each section once after checking current columns/indexes.

-- 1. ADMIN access-token cutover. The backfill time invalidates all previously issued ADMIN tokens.
ALTER TABLE admin
    ADD COLUMN token_valid_after DATETIME(6) NULL AFTER password_changed_at;

UPDATE admin
SET token_valid_after = UTC_TIMESTAMP(6)
WHERE token_valid_after IS NULL;

ALTER TABLE admin
    MODIFY COLUMN token_valid_after DATETIME(6) NOT NULL;

-- 2. Localized title compatibility columns. Legacy title stays readable during classification.
ALTER TABLE content
    ADD COLUMN title_ko VARCHAR(255) NULL AFTER title,
    ADD COLUMN title_en VARCHAR(255) NULL AFTER title_ko,
    ADD COLUMN normalized_title_ko VARCHAR(255) NULL AFTER title_en,
    ADD COLUMN normalized_title_en VARCHAR(255) NULL AFTER normalized_title_ko,
    ADD COLUMN search_title TEXT NULL AFTER normalized_title_en;

-- Compatibility seed only. CLASSIFY_ONLY stages authoritative values in tmdb_catalog_entry;
-- cleanup execute promotes them before localized-title reads are enabled.
UPDATE content
SET title_ko = title,
    normalized_title_ko = LOWER(REGEXP_REPLACE(title, '[^[:alnum:]]', '')),
    search_title = CONCAT_WS(' ', title, LOWER(REGEXP_REPLACE(title, '[^[:alnum:]]', '')))
WHERE title_ko IS NULL AND title IS NOT NULL;

-- Keep the old title FULLTEXT index until localized-title reads are enabled and verified.
ALTER TABLE content
    ADD FULLTEXT INDEX ft_content_search_title_ngram (search_title) WITH PARSER ngram;

-- 3. TMDB provider master. tmdb_provider_id remains nullable to preserve unmatched legacy rows.
ALTER TABLE ott_provider
    ADD COLUMN tmdb_provider_id BIGINT NULL AFTER url,
    ADD COLUMN display_priority INT NOT NULL DEFAULT 9999 AFTER tmdb_provider_id,
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE AFTER display_priority,
    ADD UNIQUE KEY uk_ott_provider_tmdb_provider_id (tmdb_provider_id),
    ADD KEY idx_ott_provider_active_priority (active, display_priority, id);

-- 4. Catalog registry. PENDING/RETRY are never deletion candidates.
CREATE TABLE tmdb_catalog_entry (
    id BIGINT NOT NULL,
    media_type VARCHAR(16) NOT NULL,
    tmdb_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
	title_ko VARCHAR(255) NULL,
	title_en VARCHAR(255) NULL,
	normalized_title_ko VARCHAR(255) NULL,
	normalized_title_en VARCHAR(255) NULL,
	search_title TEXT NULL,
    last_seen_export_date DATE NULL,
    last_synced_at DATETIME(6) NULL,
    next_refresh_at DATETIME(6) NULL,
    error_message VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tmdb_catalog_media_tmdb (media_type, tmdb_id),
    KEY idx_tmdb_catalog_refresh (media_type, status, next_refresh_at, id),
    KEY idx_tmdb_catalog_export_seen (last_seen_export_date, media_type, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Existing rows deliberately start PENDING. The classify-only run must resolve all of them.
INSERT INTO tmdb_catalog_entry (
    id, media_type, tmdb_id, status, created_at, updated_at
)
SELECT id, media_type, tmdb_id, 'PENDING', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM content
ON DUPLICATE KEY UPDATE updated_at = VALUES(updated_at);

-- Run after initial classification to spread recurring refresh load over 30 days.
-- UPDATE tmdb_catalog_entry
-- SET next_refresh_at = DATE_ADD(UTC_TIMESTAMP(6), INTERVAL MOD(ABS(id), 30) DAY)
-- WHERE status = 'SYNCED';

-- 5. Cross-instance workflow ownership and progress.
CREATE TABLE tmdb_sync_run (
    id BIGINT NOT NULL,
    run_key VARCHAR(64) NOT NULL,
    run_type VARCHAR(16) NOT NULL,
    business_date DATE NOT NULL,
    status VARCHAR(16) NOT NULL,
    job_execution_id BIGINT NULL,
    owner_id VARCHAR(64) NULL,
    lease_until DATETIME(6) NULL,
    heartbeat_at DATETIME(6) NULL,
    processed_count BIGINT NOT NULL DEFAULT 0,
    total_count BIGINT NOT NULL DEFAULT 0,
    error_message VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tmdb_sync_run_key (run_key),
    KEY idx_tmdb_sync_run_restart (status, lease_until, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tmdb_sync_lock (
    lock_name VARCHAR(64) NOT NULL,
    owner_id VARCHAR(64) NOT NULL,
    run_key VARCHAR(64) NOT NULL,
    lease_until DATETIME(6) NOT NULL,
    heartbeat_at DATETIME(6) NOT NULL,
    PRIMARY KEY (lock_name),
    KEY idx_tmdb_sync_lock_lease (lease_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 6. Frozen deletion manifests. Execute only after an RDS manual snapshot is confirmed.
CREATE TABLE tmdb_content_prune_manifest (
    id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    candidate_count BIGINT NOT NULL,
    candidate_hash CHAR(64) NOT NULL,
    processed_count BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    executed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_tmdb_prune_manifest_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tmdb_content_prune_candidate (
    manifest_id BIGINT NOT NULL,
    content_id BIGINT NOT NULL,
    processed BOOLEAN NOT NULL DEFAULT FALSE,
    processed_at DATETIME(6) NULL,
    PRIMARY KEY (manifest_id, content_id),
    KEY idx_tmdb_prune_candidate_work (manifest_id, processed, content_id),
    CONSTRAINT fk_tmdb_prune_candidate_manifest
        FOREIGN KEY (manifest_id) REFERENCES tmdb_content_prune_manifest (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tmdb_content_prune_collection (
    manifest_id BIGINT NOT NULL,
    collection_id BIGINT NOT NULL,
    PRIMARY KEY (manifest_id, collection_id),
    CONSTRAINT fk_tmdb_prune_collection_manifest
        FOREIGN KEY (manifest_id) REFERENCES tmdb_content_prune_manifest (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tmdb_s3_delete_queue (
	id BIGINT NOT NULL,
	object_key VARCHAR(1024) NOT NULL,
	object_key_hash BINARY(32) NOT NULL,
    delete_after DATETIME(6) NOT NULL,
    status VARCHAR(16) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at DATETIME(6) NULL,
    error_message VARCHAR(1000) NULL,
    deleted_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
	UNIQUE KEY uk_tmdb_s3_delete_object_hash (object_key_hash),
    KEY idx_tmdb_s3_delete_due (status, delete_after, next_retry_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Readiness checks before language cleanup execute.
SELECT COUNT(*) AS unresolved_content_count
FROM content c
LEFT JOIN tmdb_catalog_entry e
  ON e.tmdb_id = c.tmdb_id AND e.media_type = c.media_type
WHERE e.id IS NULL OR e.status IN ('PENDING', 'RETRY');

SELECT COUNT(*) AS missing_localized_title_count
FROM content
WHERE NULLIF(TRIM(title_ko), '') IS NULL
  AND NULLIF(TRIM(title_en), '') IS NULL;

SELECT COUNT(*) AS remaining_ineligible_content_count
FROM content c
JOIN tmdb_catalog_entry e
  ON e.tmdb_id = c.tmdb_id AND e.media_type = c.media_type
WHERE e.status = 'INELIGIBLE_LANGUAGE';

SELECT
    (SELECT COUNT(*) FROM collection_content cc LEFT JOIN content c ON c.id = cc.content_id WHERE c.id IS NULL)
        AS collection_content_orphans,
    (SELECT COUNT(*) FROM collection_content_images i LEFT JOIN collection_content cc ON cc.id = i.collection_content_id WHERE cc.id IS NULL)
        AS collection_content_image_orphans,
    (SELECT COUNT(*) FROM content_bookmark b LEFT JOIN content c ON c.id = b.content_id WHERE c.id IS NULL)
        AS content_bookmark_orphans,
    (SELECT COUNT(*) FROM ott_content o LEFT JOIN content c ON c.id = o.content_id WHERE c.id IS NULL)
        AS ott_content_orphans,
    (SELECT COUNT(*) FROM content_keywords k LEFT JOIN content c ON c.id = k.content_id WHERE c.id IS NULL)
        AS content_keyword_orphans,
    (SELECT COUNT(*) FROM content_genre g LEFT JOIN content c ON c.id = g.content_id WHERE c.id IS NULL)
        AS content_genre_orphans;

SELECT COUNT(*) AS active_empty_collection_count
FROM collection c
WHERE c.deleted_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM collection_content cc WHERE cc.collection_id = c.id);

SELECT run_key, COUNT(*) AS duplicate_count
FROM tmdb_sync_run
GROUP BY run_key
HAVING COUNT(*) > 1;

-- Inspect FULLTEXT indexes before enabling FLINT_LOCALIZED_SEARCH_ENABLED=true.
SELECT index_name, column_name, index_type
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'content'
  AND index_type = 'FULLTEXT'
ORDER BY index_name, seq_in_index;

-- Remove the legacy title FULLTEXT index only after the localized read flag is verified.
-- ALTER TABLE content DROP INDEX <verified_legacy_title_fulltext_index_name>;
