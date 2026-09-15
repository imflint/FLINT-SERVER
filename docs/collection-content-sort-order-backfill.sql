-- Backfill CollectionContent.sortOrder from existing row ids.
-- MySQL 8.0+ is required for ROW_NUMBER().
--
-- Current JPA table name: collection_content
-- Run this once before adding/enforcing uk_collection_content_sort_order.

ALTER TABLE collection_content
    ADD COLUMN sort_order INT NULL;

UPDATE collection_content cc
JOIN (
    SELECT
        id,
        ROW_NUMBER() OVER (
            PARTITION BY collection_id
            ORDER BY id ASC
        ) - 1 AS restored_sort_order
    FROM collection_content
) ranked ON ranked.id = cc.id
SET cc.sort_order = ranked.restored_sort_order;

ALTER TABLE collection_content
    MODIFY COLUMN sort_order INT NOT NULL;

ALTER TABLE collection_content
    ADD CONSTRAINT uk_collection_content_sort_order
    UNIQUE (collection_id, sort_order);
