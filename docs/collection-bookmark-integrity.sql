-- MySQL 8.0: collection bookmark/user integrity repair.
-- Prerequisite: deploy the new withdrawal/toggle code, then stop BOTH API containers
-- after enabling the maintenance response. Wait for graceful shutdown and jobs to finish.
-- Do NOT run docs/qa-data-consistency.sql again.
-- This file is a dry run by default: DML ends with ROLLBACK and DDL is commented out.
-- Keep this connection open while reviewing results. Never use mysql --force.

-- 1. Preflight: confirm the database, engines, column types and existing constraints.
SELECT DATABASE() AS target_database, @@session.foreign_key_checks AS foreign_key_checks;

SELECT table_name, engine
FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name IN ('collection_bookmark', 'user', 'collection');

SELECT table_name, column_name, column_type, is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND ((table_name = 'collection_bookmark' AND column_name = 'user_id')
       OR (table_name = 'user' AND column_name = 'id'));

SELECT constraint_name, column_name, referenced_table_name, referenced_column_name
FROM information_schema.key_column_usage
WHERE table_schema = DATABASE() AND table_name = 'collection_bookmark';

SELECT index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS indexed_columns
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'collection_bookmark'
GROUP BY index_name;

-- Require InnoDB, matching BIGINT signedness, foreign_key_checks=1 and the existing
-- unique (collection_id, user_id) constraint. Abort if these conditions do not hold.
-- If the same user FK already exists, do not add another one.

-- 2. Preview. The historical count of 10 is not an execution parameter.
SELECT COUNT(*) AS missing_user_bookmarks
FROM collection_bookmark cb
LEFT JOIN `user` u ON u.id = cb.user_id
WHERE u.id IS NULL;

SELECT cb.collection_id, COUNT(*) AS missing_user_bookmarks
FROM collection_bookmark cb
LEFT JOIN `user` u ON u.id = cb.user_id
WHERE u.id IS NULL
GROUP BY cb.collection_id
ORDER BY cb.collection_id;

SELECT c.id, c.bookmark_count AS stored_count, COUNT(cb.id) AS actual_count
FROM collection c
LEFT JOIN collection_bookmark cb ON cb.collection_id = c.id
GROUP BY c.id, c.bookmark_count
HAVING stored_count <> actual_count
ORDER BY c.id;

-- 3. Transactional DML. Delete ONLY bookmarks whose user does not exist.
START TRANSACTION;

DELETE cb
FROM collection_bookmark cb
LEFT JOIN `user` u ON u.id = cb.user_id
WHERE u.id IS NULL;

SELECT ROW_COUNT() AS deleted_orphan_bookmarks;

UPDATE collection c
LEFT JOIN (
    SELECT collection_id, COUNT(*) AS actual_count
    FROM collection_bookmark
    GROUP BY collection_id
) actual ON actual.collection_id = c.id
SET c.bookmark_count = COALESCE(actual.actual_count, 0)
WHERE c.bookmark_count <> COALESCE(actual.actual_count, 0);

SELECT ROW_COUNT() AS reconciled_collections;

-- Both checks MUST be zero. If either is nonzero, ROLLBACK and keep maintenance on.
SELECT COUNT(*) AS missing_user_bookmarks
FROM collection_bookmark cb
LEFT JOIN `user` u ON u.id = cb.user_id
WHERE u.id IS NULL;

SELECT COUNT(*) AS bookmark_count_mismatches
FROM collection c
LEFT JOIN (
    SELECT collection_id, COUNT(*) AS actual_count
    FROM collection_bookmark
    GROUP BY collection_id
) actual ON actual.collection_id = c.id
WHERE c.bookmark_count <> COALESCE(actual.actual_count, 0);

ROLLBACK;
-- For the approved execution, rerun section 3 in one connection, inspect BOTH checks,
-- and issue COMMIT instead of ROLLBACK only if both are zero. A disconnected session
-- cannot commit the previous transaction: rerun the DML if the connection was lost.

-- 4. Separate DDL AFTER the DML was explicitly committed. ALTER TABLE implicitly
-- commits, so NEVER uncomment this inside the repair transaction or a dry run.
-- Confirm the FK is absent (including equivalent FKs under another name) first.
-- SET SESSION foreign_key_checks = 1;
-- ALTER TABLE collection_bookmark
--     ADD CONSTRAINT fk_collection_bookmark_user
--     FOREIGN KEY (user_id) REFERENCES `user` (id)
--     ON DELETE RESTRICT ON UPDATE RESTRICT;

-- 5. Post-DDL verification; then start ONLY the new API version and smoke test
-- save/cancel/withdraw before removing maintenance. Keep maintenance on if DDL fails.
-- In the default dry run, ROLLBACK already restored the original rows, so the final
-- counts below can be nonzero. Only the checks BEFORE ROLLBACK verify the repair.
SELECT rc.constraint_name, rc.delete_rule, rc.update_rule,
       kcu.column_name, kcu.referenced_table_name, kcu.referenced_column_name
FROM information_schema.referential_constraints rc
JOIN information_schema.key_column_usage kcu
  ON kcu.constraint_schema = rc.constraint_schema
 AND kcu.constraint_name = rc.constraint_name
 AND kcu.table_name = rc.table_name
WHERE rc.constraint_schema = DATABASE()
  AND rc.table_name = 'collection_bookmark'
  AND kcu.column_name = 'user_id';

SELECT COUNT(*) AS missing_user_bookmarks
FROM collection_bookmark cb
LEFT JOIN `user` u ON u.id = cb.user_id
WHERE u.id IS NULL;

SELECT COUNT(*) AS bookmark_count_mismatches
FROM collection c
LEFT JOIN (
    SELECT collection_id, COUNT(*) AS actual_count
    FROM collection_bookmark
    GROUP BY collection_id
) actual ON actual.collection_id = c.id
WHERE c.bookmark_count <> COALESCE(actual.actual_count, 0);
