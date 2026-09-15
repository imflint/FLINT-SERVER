-- QA data consistency and exploration snapshot rollout (MySQL 8.0)
-- Take an RDS manual snapshot first. Run preview queries before each UPDATE.
-- This script is intentionally manual and non-idempotent.

-- 1. Exploration snapshot schema. Apply before enabling FLINT_EXPLORATION_SNAPSHOT_ENABLED.
ALTER TABLE user_exploration_progress
    ADD COLUMN session_version INT NOT NULL DEFAULT 1 AFTER completed,
    ADD COLUMN last_viewed_position INT NOT NULL DEFAULT 0 AFTER session_version;

-- Legacy completed=true means the prior session had already been consumed.
UPDATE user_exploration_progress
SET last_viewed_position = 30
WHERE completed = TRUE;

CREATE TABLE user_exploration_session_item (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    session_version INT NOT NULL,
    position INT NOT NULL,
    content_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    poster VARCHAR(1024) NULL,
    `year` INT NOT NULL,
    description TEXT NULL,
    collection_id BIGINT NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_exploration_session_position (user_id, session_version, position),
    KEY idx_exploration_session_lookup (user_id, session_version, position)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- No FK is added to content or collection. A deleted source row must not block session history cleanup.

-- 2. Collection bookmark count index and reconciliation.
-- Confirm this returns zero rows before creating the index.
SELECT index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS columns_in_order
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'collection_bookmark'
GROUP BY index_name
HAVING columns_in_order = 'collection_id'
    OR columns_in_order LIKE 'collection_id,%';

CREATE INDEX idx_collection_bookmark_collection_id
    ON collection_bookmark (collection_id);

-- Preview drift.
SELECT
    c.id,
    c.bookmark_count AS stored_count,
    COUNT(cb.id) AS actual_count
FROM collection c
LEFT JOIN collection_bookmark cb ON cb.collection_id = c.id
GROUP BY c.id, c.bookmark_count
HAVING stored_count <> actual_count
ORDER BY c.id;

-- Execute after reviewing the preview.
UPDATE collection c
LEFT JOIN (
    SELECT collection_id, COUNT(*) AS actual_count
    FROM collection_bookmark
    GROUP BY collection_id
) actual ON actual.collection_id = c.id
SET c.bookmark_count = COALESCE(actual.actual_count, 0)
WHERE c.bookmark_count <> COALESCE(actual.actual_count, 0);

-- Completion check: must be zero.
SELECT COUNT(*) AS bookmark_count_mismatch_count
FROM collection c
LEFT JOIN (
    SELECT collection_id, COUNT(*) AS actual_count
    FROM collection_bookmark
    GROUP BY collection_id
) actual ON actual.collection_id = c.id
WHERE c.bookmark_count <> COALESCE(actual.actual_count, 0);

-- 3. Existing taste keyword audit and largest-remainder normalization.
-- Users reported here need keyword re-analysis; SQL cannot infer missing selections.
SELECT user_id, COUNT(*) AS keyword_count
FROM user_keywords
GROUP BY user_id
HAVING keyword_count <> 6;

-- Preview current sums.
SELECT user_id, COUNT(*) AS keyword_count, SUM(GREATEST(percentage, 0)) AS percentage_sum
FROM user_keywords
GROUP BY user_id
HAVING keyword_count <> 6 OR percentage_sum <> 100;

CREATE TEMPORARY TABLE tmp_user_keyword_normalized AS
WITH ranked AS (
    SELECT
        uk.id,
        uk.user_id,
        ROW_NUMBER() OVER (
            PARTITION BY uk.user_id
            ORDER BY uk.ranking, uk.percentage DESC, uk.id
        ) AS normalized_rank,
        COUNT(*) OVER (PARTITION BY uk.user_id) AS keyword_count,
        GREATEST(uk.percentage, 0) AS weight,
        SUM(GREATEST(uk.percentage, 0)) OVER (PARTITION BY uk.user_id) AS total_weight
    FROM user_keywords uk
), base AS (
    SELECT
        ranked.*,
        FLOOR(
            CASE
                WHEN total_weight = 0 THEN 100.0 / keyword_count
                ELSE weight * 100.0 / total_weight
            END
        ) AS base_percentage,
        CASE
            WHEN total_weight = 0 THEN 100.0 / keyword_count
            ELSE weight * 100.0 / total_weight
        END - FLOOR(
            CASE
                WHEN total_weight = 0 THEN 100.0 / keyword_count
                ELSE weight * 100.0 / total_weight
            END
        ) AS fractional_remainder
    FROM ranked
), apportioned AS (
    SELECT
        base.*,
        100 - SUM(base_percentage) OVER (PARTITION BY user_id) AS remaining_points,
        ROW_NUMBER() OVER (
            PARTITION BY user_id
            ORDER BY fractional_remainder DESC, normalized_rank, id
        ) AS remainder_order
    FROM base
) SELECT
    id,
    normalized_rank,
    base_percentage + IF(remainder_order <= remaining_points, 1, 0) AS normalized_percentage
FROM apportioned;

ALTER TABLE tmp_user_keyword_normalized ADD PRIMARY KEY (id);

UPDATE user_keywords uk
JOIN tmp_user_keyword_normalized normalized ON normalized.id = uk.id
SET uk.ranking = normalized.normalized_rank,
    uk.percentage = normalized.normalized_percentage,
    uk.updated_at = UTC_TIMESTAMP(6);

DROP TEMPORARY TABLE tmp_user_keyword_normalized;

-- Completion check. Users with exactly six rows must total 100 and have ranks 1..6.
SELECT
    user_id,
    COUNT(*) AS keyword_count,
    SUM(percentage) AS percentage_sum,
    MIN(ranking) AS min_rank,
    MAX(ranking) AS max_rank,
    COUNT(DISTINCT ranking) AS distinct_rank_count
FROM user_keywords
GROUP BY user_id
HAVING keyword_count = 6
   AND (percentage_sum <> 100 OR min_rank <> 1 OR max_rank <> 6 OR distinct_rank_count <> 6);

-- 4. Missing author cleanup. API also maps these legacy sentinel values to null during rollout.
SELECT id, media_type, title, author
FROM content
WHERE TRIM(COALESCE(author, '')) = ''
   OR LOWER(TRIM(author)) = 'unknown';

UPDATE content
SET author = NULL
WHERE TRIM(COALESCE(author, '')) = ''
   OR LOWER(TRIM(author)) = 'unknown';

-- Rollout order:
-- 1) Apply this schema and backfills in dev.
-- 2) Deploy the API with FLINT_EXPLORATION_SNAPSHOT_ENABLED=false.
-- 3) Run API smoke tests, then set the flag true in dev.
-- 4) Repeat snapshot, DDL, deploy, smoke test, and flag enablement in prod.
