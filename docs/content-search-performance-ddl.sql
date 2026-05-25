-- MySQL 8 DDL for GET /api/v1/contents/search.
-- Apply these indexes before deploying the FULLTEXT search path.
-- If an index already exists, skip that statement manually. MySQL 8 does not support
-- CREATE INDEX IF NOT EXISTS consistently across target versions.

ALTER TABLE content
	ADD FULLTEXT INDEX ft_content_title_ngram (title) WITH PARSER ngram;

CREATE INDEX idx_content_popular
	ON content (bookmark_count DESC, id DESC);

CREATE INDEX idx_content_media_popular
	ON content (media_type, bookmark_count DESC, id DESC);

CREATE INDEX idx_content_genre_genre_content
	ON content_genre (genre_id, content_id);
