-- MySQL 8 DDL for GET /api/v1/contents/search.
-- Apply these indexes before deploying the FULLTEXT search path.
-- If an index already exists, skip that statement manually. MySQL 8 does not support
-- CREATE INDEX IF NOT EXISTS consistently across target versions.
-- Run the preflight first, then execute ONLY missing statements individually.
-- Stop if title/normalized_title_* are not VARCHAR(255) using utf8mb4, or if
-- an existing index with the same name has a different expression/order/parser.
-- Functional equality must keep the column's existing collation (no CAST).
-- Run functional-index creation only in an approved maintenance window:
-- LOCK=SHARED allows reads but blocks writes while the index is built.
-- Other LOCK=NONE/INPLACE failures must not be retried with a blocking algorithm.

SELECT VERSION(), @@ngram_token_size;

SELECT column_name, column_type, character_set_name, collation_name
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'content'
  AND column_name IN ('title', 'normalized_title_ko', 'normalized_title_en', 'search_title');

SELECT table_name, index_name, non_unique, seq_in_index, column_name,
       expression, collation, index_type, is_visible
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name IN ('content', 'content_genre')
ORDER BY table_name, index_name, seq_in_index;

SHOW CREATE TABLE content;
SHOW CREATE TABLE content_genre;

ALTER TABLE content
	ADD FULLTEXT INDEX ft_content_title_ngram (title) WITH PARSER ngram;

-- Required only when localized-search-enabled=true; normally already present.
ALTER TABLE content
    ADD FULLTEXT INDEX ft_content_search_title_ngram (search_title) WITH PARSER ngram;

CREATE INDEX idx_content_popular
	ON content (bookmark_count DESC, id DESC);

CREATE INDEX idx_content_media_popular
	ON content (media_type, bookmark_count DESC, id DESC);

CREATE INDEX idx_content_genre_genre_content
	ON content_genre (genre_id, content_id);

-- New exact-match indexes. These do not change data or search semantics.
CREATE INDEX idx_content_title_lower
    ON content ((LOWER(title))) ALGORITHM=INPLACE LOCK=SHARED;

CREATE INDEX idx_content_normalized_title_ko
    ON content (normalized_title_ko) ALGORITHM=INPLACE LOCK=NONE;

CREATE INDEX idx_content_normalized_title_en
    ON content (normalized_title_en) ALGORITHM=INPLACE LOCK=NONE;

-- uk_content_genre must be UNIQUE (content_id, genre_id). It is an existing
-- entity/database constraint: verify it; do not create a duplicate index.
-- For rollout, lock behavior, validation queries and rollback, see
-- docs/content-search-performance.md. No production execution is automated.
