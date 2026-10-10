-- MySQL 8.4.8, MANUAL maintenance only. No production execution is automated.
-- Execute preflight, then DEFINITIONS in ONE connection. CALLs below are comments.
-- Stop API/admin writes and ALL TMDB jobs first. DDL implicitly commits.
-- Backups must be exported off DB before finish. Do not rerun historical cleanup SQL.
-- Stages: BACKED_UP -> PREPARED -> RELATIONS_DONE -> FINALIZING -> COMPLETED.
-- Retrying a stage never replaces its original snapshot. Failure keeps maintenance on.
SET @genre_apply = 0;
SET @maintenance_confirmed = 0;
SET @genre_commit_chunk = 0;
SET @genre_backup_exported = 0;
SET @expected_database = 'flint';
SET @genre_run_key = 'genre-normalization-v1';
SELECT DATABASE(), VERSION(), @@ngram_token_size;
SELECT g.id, g.name, COUNT(cg.id) AS relations FROM genre g
LEFT JOIN content_genre cg ON cg.genre_id=g.id GROUP BY g.id,g.name ORDER BY g.id;
SELECT table_name, engine FROM information_schema.tables
WHERE table_schema=DATABASE() AND table_name IN ('genre','content_genre','content');
SELECT table_name,column_name,column_type FROM information_schema.columns
WHERE table_schema=DATABASE() AND table_name IN ('genre','content_genre')
AND column_name IN ('id','genre_id','content_id','name','code');
SELECT table_name,column_name,constraint_name FROM information_schema.key_column_usage
WHERE referenced_table_schema=DATABASE() AND referenced_table_name='genre';

-- DEFINITIONS: explicit aliases, never split a compound name into multiple genres.
DROP TEMPORARY TABLE IF EXISTS tmp_genre_alias;
CREATE TEMPORARY TABLE tmp_genre_alias (
 alias VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs PRIMARY KEY,
 code VARCHAR(32) NOT NULL, display_name VARCHAR(255) NOT NULL
);
INSERT INTO tmp_genre_alias VALUES
 ('액션','ACTION','액션'),
 ('action','ACTION','액션'),
 ('action & adventure','ACTION','액션'),
 ('action, action & adventure','ACTION','액션'),
 ('모험','ADVENTURE','모험'),
 ('adventure','ADVENTURE','모험'),
 ('애니메이션','ANIMATION','애니메이션'),
 ('animation','ANIMATION','애니메이션'),
 ('코미디','COMEDY','코미디'),
 ('comedy','COMEDY','코미디'),
 ('범죄','CRIME','범죄'),
 ('crime','CRIME','범죄'),
 ('다큐멘터리','DOCUMENTARY','다큐멘터리'),
 ('documentary','DOCUMENTARY','다큐멘터리'),
 ('드라마','DRAMA','드라마'),
 ('drama','DRAMA','드라마'),
 ('가족','FAMILY','가족'),
 ('family','FAMILY','가족'),
 ('판타지','FANTASY','판타지'),
 ('fantasy','FANTASY','판타지'),
 ('역사','HISTORY','역사'),
 ('history','HISTORY','역사'),
 ('공포','HORROR','공포'),
 ('horror','HORROR','공포'),
 ('호러','HORROR','공포'),
 ('음악','MUSIC','음악'),
 ('music','MUSIC','음악'),
 ('미스터리','MYSTERY','미스터리'),
 ('mystery','MYSTERY','미스터리'),
 ('로맨스','ROMANCE','로맨스'),
 ('romance','ROMANCE','로맨스'),
 ('sf','SCIENCE_FICTION','SF'),
 ('science_fiction','SCIENCE_FICTION','SF'),
 ('science fiction','SCIENCE_FICTION','SF'),
 ('sci-fi','SCIENCE_FICTION','SF'),
 ('sci-fi & fantasy','SCIENCE_FICTION','SF'),
 ('science fiction, sci-fi & fantasy','SCIENCE_FICTION','SF'),
 ('tv 영화','TV_MOVIE','TV 영화'),
 ('tv_movie','TV_MOVIE','TV 영화'),
 ('tv movie','TV_MOVIE','TV 영화'),
 ('스릴러','THRILLER','스릴러'),
 ('thriller','THRILLER','스릴러'),
 ('전쟁','WAR','전쟁'),
 ('war','WAR','전쟁'),
 ('war & politics','WAR','전쟁'),
 ('war, war & politics','WAR','전쟁'),
 ('서부','WESTERN','서부'),
 ('western','WESTERN','서부'),
 ('어린이','KIDS','어린이'),
 ('kids','KIDS','어린이'),
 ('뉴스','NEWS','뉴스'),
 ('news','NEWS','뉴스'),
 ('리얼리티','REALITY','리얼리티'),
 ('reality','REALITY','리얼리티'),
 ('연속극','SOAP','연속극'),
 ('soap','SOAP','연속극'),
 ('토크','TALK','토크'),
 ('talk','TALK','토크');
DROP TEMPORARY TABLE IF EXISTS tmp_tmdb_genre_seed;
CREATE TEMPORARY TABLE tmp_tmdb_genre_seed (
 media_type VARCHAR(16), tmdb_genre_id BIGINT, code VARCHAR(32),
 PRIMARY KEY(media_type,tmdb_genre_id)
);
INSERT INTO tmp_tmdb_genre_seed VALUES
 ('MOVIE',28,'ACTION'),
 ('MOVIE',12,'ADVENTURE'),
 ('MOVIE',16,'ANIMATION'),
 ('MOVIE',35,'COMEDY'),
 ('MOVIE',80,'CRIME'),
 ('MOVIE',99,'DOCUMENTARY'),
 ('MOVIE',18,'DRAMA'),
 ('MOVIE',10751,'FAMILY'),
 ('MOVIE',14,'FANTASY'),
 ('MOVIE',36,'HISTORY'),
 ('MOVIE',27,'HORROR'),
 ('MOVIE',10402,'MUSIC'),
 ('MOVIE',9648,'MYSTERY'),
 ('MOVIE',10749,'ROMANCE'),
 ('MOVIE',878,'SCIENCE_FICTION'),
 ('MOVIE',10770,'TV_MOVIE'),
 ('MOVIE',53,'THRILLER'),
 ('MOVIE',10752,'WAR'),
 ('MOVIE',37,'WESTERN'),
 ('TV',10759,'ACTION'),
 ('TV',16,'ANIMATION'),
 ('TV',35,'COMEDY'),
 ('TV',80,'CRIME'),
 ('TV',99,'DOCUMENTARY'),
 ('TV',18,'DRAMA'),
 ('TV',10751,'FAMILY'),
 ('TV',10762,'KIDS'),
 ('TV',9648,'MYSTERY'),
 ('TV',10763,'NEWS'),
 ('TV',10764,'REALITY'),
 ('TV',10765,'SCIENCE_FICTION'),
 ('TV',10766,'SOAP'),
 ('TV',10767,'TALK'),
 ('TV',10768,'WAR'),
 ('TV',37,'WESTERN');

DELIMITER $$
DROP PROCEDURE IF EXISTS flint_genre_backup$$
CREATE PROCEDURE flint_genre_backup(IN p_run_key VARCHAR(64))
backup_stage: BEGIN
 DECLARE missing INT DEFAULT 0;
 DECLARE code_exists BOOLEAN DEFAULT FALSE;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF COALESCE(@genre_apply,0)<>1 OR COALESCE(@maintenance_confirmed,0)<>1
 OR DATABASE() IS NULL OR @expected_database IS NULL OR DATABASE()<>@expected_database
 OR p_run_key IS NULL OR p_run_key='' THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Genre migration requires explicit maintenance approval/database/run key';
 END IF;
 SELECT COUNT(*) INTO missing FROM information_schema.tables
 WHERE table_schema=DATABASE() AND table_name IN ('genre','content_genre','content') AND engine='InnoDB';
 IF missing<>3 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Expected InnoDB tables missing'; END IF;
 SELECT COUNT(*) INTO missing FROM information_schema.key_column_usage
 WHERE referenced_table_schema=DATABASE() AND referenced_table_name='genre'
 AND (table_name NOT IN ('content_genre','tmdb_genre_mapping') OR referenced_column_name<>'id');
 IF missing<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Unexpected genre references; stop and review'; END IF;
 SELECT COUNT(*) INTO missing FROM information_schema.triggers
 WHERE trigger_schema=DATABASE() AND event_object_table IN ('genre','content_genre');
 IF missing<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Unexpected genre trigger'; END IF;
 SELECT COUNT(*) INTO missing FROM genre g LEFT JOIN tmp_genre_alias a ON a.alias=LOWER(TRIM(g.name)) COLLATE utf8mb4_0900_as_cs
 WHERE a.code IS NULL;
 IF missing<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Unmapped genre name; no guessing permitted'; END IF;
 SELECT COUNT(DISTINCT a.code) INTO missing FROM genre g
 JOIN tmp_genre_alias a ON a.alias=LOWER(TRIM(g.name)) COLLATE utf8mb4_0900_as_cs;
 IF missing<>24 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='All 24 canonical genre source groups are required'; END IF;
 SELECT COUNT(*) INTO missing FROM content_genre cg
 LEFT JOIN genre g ON g.id=cg.genre_id LEFT JOIN content c ON c.id=cg.content_id
 WHERE g.id IS NULL OR c.id IS NULL;
 IF missing<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Existing orphan genre relations; stop'; END IF;
 CREATE TABLE IF NOT EXISTS genre_normalization_run (
  run_key VARCHAR(64) PRIMARY KEY, last_content_id BIGINT NOT NULL DEFAULT 0,
  status VARCHAR(16) NOT NULL, code_index_owned BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME(6) NOT NULL
 ) ENGINE=InnoDB;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
 AND table_name='genre_normalization_run' AND column_name='code_index_owned') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Legacy run schema requires review; never overwrite its backups';
 END IF;
 IF EXISTS(SELECT 1 FROM genre_normalization_run WHERE run_key=p_run_key AND status='RESTORED') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Restored run requires a new run key';
 END IF;
 IF EXISTS(SELECT 1 FROM genre_normalization_run WHERE run_key=p_run_key) THEN
  SELECT status,last_content_id FROM genre_normalization_run WHERE run_key=p_run_key;
  LEAVE backup_stage;
 END IF;
 CREATE TABLE IF NOT EXISTS genre_normalization_schema_backup (
  run_key VARCHAR(64) PRIMARY KEY, code_existed BOOLEAN NOT NULL,
  columns_json JSON NOT NULL, indexes_json JSON NOT NULL
 ) ENGINE=InnoDB;
 CREATE TABLE IF NOT EXISTS genre_normalization_genre_backup (
  run_key VARCHAR(64), id BIGINT, name VARCHAR(255), old_code VARCHAR(32),
  canonical_id BIGINT NOT NULL, code VARCHAR(32) NOT NULL, display_name VARCHAR(255) NOT NULL,
  PRIMARY KEY(run_key,id)
 ) ENGINE=InnoDB;
 CREATE TABLE IF NOT EXISTS genre_normalization_relation_backup (
  run_key VARCHAR(64), id BIGINT, content_id BIGINT NOT NULL, genre_id BIGINT NOT NULL,
  PRIMARY KEY(run_key,id), KEY idx_genre_backup_content(run_key,content_id)
 ) ENGINE=InnoDB;
 CREATE TABLE IF NOT EXISTS genre_normalization_tmdb_backup (
  run_key VARCHAR(64),media_type VARCHAR(16),tmdb_genre_id BIGINT,genre_id BIGINT NOT NULL,
  PRIMARY KEY(run_key,media_type,tmdb_genre_id)
 ) ENGINE=InnoDB;
 SELECT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
 AND table_name='genre' AND column_name='code') INTO code_exists;
 IF code_exists THEN
  SELECT COUNT(*) INTO missing FROM genre WHERE CHAR_LENGTH(code)>32;
  IF missing<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Existing code exceeds snapshot capacity'; END IF;
 END IF;
 DROP TEMPORARY TABLE IF EXISTS tmp_genre_choice_alias;
 CREATE TEMPORARY TABLE tmp_genre_choice_alias AS SELECT * FROM tmp_genre_alias;
 START TRANSACTION;
 INSERT INTO genre_normalization_schema_backup
 SELECT p_run_key,code_exists,
  (SELECT JSON_ARRAYAGG(JSON_OBJECT('table',table_name,'column',column_name,'type',column_type,
   'nullable',is_nullable,'default',column_default,'charset',character_set_name,'collation',collation_name,'extra',extra))
   FROM information_schema.columns WHERE table_schema=DATABASE()
   AND table_name IN ('genre','content_genre','tmdb_genre_mapping')),
  (SELECT JSON_ARRAYAGG(JSON_OBJECT('table',table_name,'index',index_name,'non_unique',non_unique,
   'position',seq_in_index,'column',column_name,'expression',expression,'type',index_type,'visible',is_visible))
   FROM information_schema.statistics WHERE table_schema=DATABASE()
   AND table_name IN ('genre','content_genre','tmdb_genre_mapping'));
 INSERT INTO genre_normalization_genre_backup
 SELECT p_run_key,g.id,g.name,NULL,chosen.id,a.code,a.display_name
 FROM genre g JOIN tmp_genre_alias a ON a.alias=LOWER(TRIM(g.name)) COLLATE utf8mb4_0900_as_cs
 JOIN (
  SELECT id,code FROM (
   SELECT src.id,alias.code,ROW_NUMBER() OVER(PARTITION BY alias.code
    ORDER BY (BINARY src.name=BINARY alias.display_name) DESC,COALESCE(usage_count.n,0) DESC,src.id) AS rn
   FROM genre src JOIN tmp_genre_choice_alias alias ON alias.alias=LOWER(TRIM(src.name)) COLLATE utf8mb4_0900_as_cs
   LEFT JOIN (SELECT genre_id,COUNT(*) n FROM content_genre GROUP BY genre_id) usage_count ON usage_count.genre_id=src.id
  ) ranked WHERE rn=1
 ) chosen ON chosen.code=a.code;
 IF code_exists THEN
  UPDATE genre_normalization_genre_backup b JOIN genre g ON g.id=b.id
  SET b.old_code=g.code WHERE b.run_key=p_run_key;
 END IF;
 INSERT INTO genre_normalization_relation_backup SELECT p_run_key,id,content_id,genre_id FROM content_genre;
 IF EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='tmdb_genre_mapping') THEN
  INSERT INTO genre_normalization_tmdb_backup SELECT p_run_key,media_type,tmdb_genre_id,genre_id FROM tmdb_genre_mapping;
 END IF;
 INSERT INTO genre_normalization_run VALUES(p_run_key,0,'BACKED_UP',FALSE,UTC_TIMESTAMP(6));
 COMMIT;
END$$

DROP PROCEDURE IF EXISTS flint_genre_prepare$$
CREATE PROCEDURE flint_genre_prepare(IN p_run_key VARCHAR(64))
prepare_stage: BEGIN
 DECLARE stage VARCHAR(16);
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 CALL flint_genre_backup(p_run_key);
 SELECT status INTO stage FROM genre_normalization_run WHERE run_key=p_run_key;
 IF stage IN ('PREPARED','RELATIONS_DONE','FINALIZING','COMPLETED') THEN LEAVE prepare_stage; END IF;
 IF stage<>'BACKED_UP' OR COALESCE(@genre_backup_exported,0)<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Export original backups before preparing; explicit confirmation required';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
 AND table_name='genre' AND column_name='code') THEN
  ALTER TABLE genre ADD COLUMN code VARCHAR(32) NULL;
 ELSEIF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
 AND table_name='genre' AND column_name='code' AND column_type='varchar(32)' AND is_nullable='YES') THEN
  ALTER TABLE genre MODIFY code VARCHAR(32) NULL;
 END IF;
 CREATE TABLE IF NOT EXISTS tmdb_genre_mapping (
  media_type VARCHAR(16) NOT NULL, tmdb_genre_id BIGINT NOT NULL, genre_id BIGINT NOT NULL,
  PRIMARY KEY(media_type,tmdb_genre_id),
  CONSTRAINT fk_tmdb_genre_mapping_genre FOREIGN KEY(genre_id) REFERENCES genre(id) ON DELETE RESTRICT ON UPDATE RESTRICT
 ) ENGINE=InnoDB;
 START TRANSACTION;
 -- Clear all codes before assigning winners, so existing UNIQUE constraints cannot collide mid-update.
 UPDATE genre g JOIN genre_normalization_genre_backup b ON b.id=g.id AND b.run_key=p_run_key SET g.code=NULL;
 UPDATE genre g JOIN genre_normalization_genre_backup b ON b.id=g.id AND b.run_key=p_run_key
 SET g.code=b.code WHERE b.id=b.canonical_id;
 UPDATE genre_normalization_run SET status='PREPARED' WHERE run_key=p_run_key;
 COMMIT;
END$$

DROP PROCEDURE IF EXISTS flint_genre_chunk$$
CREATE PROCEDURE flint_genre_chunk(IN p_run_key VARCHAR(64))
chunk_stage: BEGIN
 DECLARE last_id BIGINT;
 DECLARE next_id BIGINT;
 DECLARE expected_count BIGINT;
 DECLARE actual_count BIGINT;
 DECLARE stage VARCHAR(16);
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF COALESCE(@genre_apply,0)<>1 OR COALESCE(@maintenance_confirmed,0)<>1
 OR DATABASE() IS NULL OR @expected_database IS NULL OR DATABASE()<>@expected_database THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Maintenance approval required';
 END IF;
 START TRANSACTION;
 SELECT r.last_content_id,r.status INTO last_id,stage FROM genre_normalization_run r
 WHERE r.run_key=p_run_key FOR UPDATE;
 IF stage IN ('RELATIONS_DONE','FINALIZING','COMPLETED') THEN
  ROLLBACK;
  SELECT last_id AS previous_id,NULL AS next_id,0 AS remaining_relations;
  LEAVE chunk_stage;
 END IF;
 IF last_id IS NULL OR stage<>'PREPARED' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Prepared run missing'; END IF;
 DROP TEMPORARY TABLE IF EXISTS tmp_genre_content_chunk;
 CREATE TEMPORARY TABLE tmp_genre_content_chunk(content_id BIGINT PRIMARY KEY);
 INSERT INTO tmp_genre_content_chunk SELECT DISTINCT b.content_id FROM genre_normalization_relation_backup b
 WHERE b.run_key=p_run_key AND b.content_id>last_id ORDER BY b.content_id LIMIT 500;
 SELECT MAX(content_id) INTO next_id FROM tmp_genre_content_chunk;
 DROP TEMPORARY TABLE IF EXISTS tmp_genre_relation_winner;
 CREATE TEMPORARY TABLE tmp_genre_relation_winner AS
 SELECT cg.id,cg.content_id,b.canonical_id,
 ROW_NUMBER() OVER(PARTITION BY cg.content_id,b.canonical_id
  ORDER BY (cg.genre_id=b.canonical_id) DESC,cg.id) AS rn
 FROM content_genre cg JOIN tmp_genre_content_chunk chunk ON chunk.content_id=cg.content_id
 JOIN genre_normalization_genre_backup b ON b.run_key=p_run_key AND b.id=cg.genre_id;
 DELETE cg FROM content_genre cg JOIN tmp_genre_relation_winner w ON w.id=cg.id WHERE w.rn>1;
 UPDATE content_genre cg JOIN tmp_genre_relation_winner w ON w.id=cg.id AND w.rn=1 SET cg.genre_id=w.canonical_id;
 SELECT COUNT(*) INTO expected_count FROM tmp_genre_relation_winner WHERE rn=1;
 SELECT COUNT(*) INTO actual_count FROM content_genre cg JOIN tmp_genre_content_chunk chunk ON chunk.content_id=cg.content_id;
 IF expected_count<>actual_count THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Chunk relation verification failed'; END IF;
 UPDATE genre_normalization_run r SET r.last_content_id=COALESCE(next_id,last_id),
  r.status=IF(EXISTS(SELECT 1 FROM genre_normalization_relation_backup b WHERE b.run_key=p_run_key
   AND b.content_id>COALESCE(next_id,last_id)),'PREPARED','RELATIONS_DONE') WHERE r.run_key=p_run_key;
 SELECT last_id AS previous_id,next_id AS next_id,expected_count AS remaining_relations;
 IF COALESCE(@genre_commit_chunk,0)=1 THEN COMMIT; ELSE ROLLBACK; END IF;
END$$

DROP PROCEDURE IF EXISTS flint_genre_finish$$
CREATE PROCEDURE flint_genre_finish(IN p_run_key VARCHAR(64))
finish_stage: BEGIN
 DECLARE remaining BIGINT;
 DECLARE stage VARCHAR(16);
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF COALESCE(@genre_apply,0)<>1 OR COALESCE(@maintenance_confirmed,0)<>1
 OR COALESCE(@genre_commit_chunk,0)<>1 OR DATABASE() IS NULL OR @expected_database IS NULL
 OR DATABASE()<>@expected_database THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Explicit commit approval required';
 END IF;
 SELECT status INTO stage FROM genre_normalization_run WHERE run_key=p_run_key;
 IF stage='COMPLETED' THEN LEAVE finish_stage; END IF;
 SELECT COUNT(*) INTO remaining FROM genre_normalization_relation_backup b
 JOIN genre_normalization_run r ON r.run_key=b.run_key
 WHERE b.run_key=p_run_key AND b.content_id>r.last_content_id;
 IF remaining<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Chunks not finished'; END IF;
 IF stage IS NULL OR stage NOT IN ('RELATIONS_DONE','FINALIZING') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Prepared run missing';
 END IF;
 SELECT COUNT(*) INTO remaining FROM content_genre cg JOIN genre_normalization_genre_backup b
 ON b.run_key=p_run_key AND b.id=cg.genre_id WHERE b.id<>b.canonical_id;
 IF remaining<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Alias references remain'; END IF;
 -- Prove that exactly the projected original relation set remains, not just matching row counts.
 SELECT COUNT(*) INTO remaining FROM (
  SELECT old.content_id,g.canonical_id FROM genre_normalization_relation_backup old
  JOIN genre_normalization_genre_backup g ON g.run_key=old.run_key AND g.id=old.genre_id
  LEFT JOIN content_genre cg ON cg.content_id=old.content_id AND cg.genre_id=g.canonical_id
  WHERE old.run_key=p_run_key AND cg.id IS NULL
 ) lost;
 IF remaining<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Original projected relation missing'; END IF;
 SELECT COUNT(*) INTO remaining FROM content_genre cg
 LEFT JOIN genre_normalization_relation_backup b ON b.run_key=p_run_key AND b.id=cg.id WHERE b.id IS NULL;
 IF remaining<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Unexpected writes after backup'; END IF;
 START TRANSACTION;
 UPDATE tmdb_genre_mapping m JOIN genre_normalization_genre_backup b ON b.run_key=p_run_key AND b.id=m.genre_id
 SET m.genre_id=b.canonical_id;
 DELETE g FROM genre g JOIN genre_normalization_genre_backup b ON b.run_key=p_run_key AND b.id=g.id
 WHERE b.id<>b.canonical_id;
 UPDATE genre g JOIN genre_normalization_genre_backup b ON b.run_key=p_run_key AND b.id=g.id SET g.name=b.display_name;
 IF EXISTS(SELECT 1 FROM genre WHERE code IS NULL) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Null genre code remains'; END IF;
 UPDATE genre_normalization_run SET status='FINALIZING' WHERE run_key=p_run_key;
 COMMIT;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
 AND table_name='genre' AND column_name='code' AND column_type='varchar(32)' AND is_nullable='NO') THEN
  ALTER TABLE genre MODIFY code VARCHAR(32) NOT NULL;
 END IF;
 IF EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
 AND table_name='genre' AND index_name='uk_genre_code') AND NOT EXISTS(
  SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='genre' AND index_name='uk_genre_code' GROUP BY index_name
  HAVING COUNT(*)=1 AND MIN(column_name)='code' AND MAX(non_unique)=0
   AND MAX(sub_part IS NOT NULL)=0 AND MIN(index_type)='BTREE'
   AND MIN(is_visible)='YES' AND MIN(collation)='A'
 ) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Existing uk_genre_code definition differs'; END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
 AND table_name='genre' AND index_name='uk_genre_code') THEN
  -- Persist ownership before atomic DDL, so a disconnect after ADD can be resumed/restored safely.
  UPDATE genre_normalization_run SET code_index_owned=TRUE WHERE run_key=p_run_key;
  COMMIT;
  ALTER TABLE genre ADD UNIQUE KEY uk_genre_code(code);
 END IF;
 START TRANSACTION;
 INSERT INTO tmdb_genre_mapping(media_type,tmdb_genre_id,genre_id)
 SELECT seed.media_type,seed.tmdb_genre_id,g.id FROM tmp_tmdb_genre_seed seed JOIN genre g ON g.code=seed.code
 ON DUPLICATE KEY UPDATE genre_id=VALUES(genre_id);
 UPDATE genre_normalization_run r SET r.status='COMPLETED' WHERE r.run_key=p_run_key;
 COMMIT;
END$$

DROP PROCEDURE IF EXISTS flint_genre_restore$$
CREATE PROCEDURE flint_genre_restore(IN p_run_key VARCHAR(64))
BEGIN
 DECLARE unexpected BIGINT;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF COALESCE(@genre_apply,0)<>1 OR COALESCE(@maintenance_confirmed,0)<>1
 OR COALESCE(@genre_commit_chunk,0)<>1 OR DATABASE() IS NULL OR @expected_database IS NULL
 OR DATABASE()<>@expected_database THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Restore requires explicit maintenance/commit approval';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM genre_normalization_run r WHERE r.run_key=p_run_key
 AND r.status IN ('BACKED_UP','PREPARED','RELATIONS_DONE','FINALIZING','COMPLETED')) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Restorable run missing';
 END IF;
 SELECT COUNT(*) INTO unexpected FROM content_genre cg
 LEFT JOIN genre_normalization_relation_backup b ON b.run_key=p_run_key AND b.id=cg.id WHERE b.id IS NULL;
 IF unexpected<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='New relation writes found; automatic restore refused'; END IF;
 SELECT COUNT(*) INTO unexpected FROM genre g
 LEFT JOIN genre_normalization_genre_backup b ON b.run_key=p_run_key AND b.id=g.id WHERE b.id IS NULL;
 IF unexpected<>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='New genre writes found; automatic restore refused'; END IF;
 -- Additive DDL is intentionally retained; the old API ignores nullable code.
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
 AND table_name='genre' AND column_name='code') THEN ALTER TABLE genre ADD COLUMN code VARCHAR(32) NULL;
 ELSE ALTER TABLE genre MODIFY code VARCHAR(32) NULL; END IF;
 CREATE TABLE IF NOT EXISTS tmdb_genre_mapping (
  media_type VARCHAR(16) NOT NULL, tmdb_genre_id BIGINT NOT NULL, genre_id BIGINT NOT NULL,
  PRIMARY KEY(media_type,tmdb_genre_id),
  CONSTRAINT fk_tmdb_genre_mapping_genre FOREIGN KEY(genre_id) REFERENCES genre(id) ON DELETE RESTRICT ON UPDATE RESTRICT
 ) ENGINE=InnoDB;
 IF EXISTS(SELECT 1 FROM genre_normalization_run WHERE run_key=p_run_key AND code_index_owned=TRUE)
 AND EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
 AND table_name='genre' AND index_name='uk_genre_code') THEN
  IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
   AND table_name='genre' AND index_name='uk_genre_code' GROUP BY index_name
   HAVING COUNT(*)=1 AND MIN(column_name)='code' AND MAX(non_unique)=0
    AND MAX(sub_part IS NOT NULL)=0 AND MIN(index_type)='BTREE'
    AND MIN(is_visible)='YES' AND MIN(collation)='A') THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Owned index changed; restore refused';
  END IF;
  ALTER TABLE genre DROP INDEX uk_genre_code;
 END IF;
 START TRANSACTION;
 DELETE FROM tmdb_genre_mapping;
 DELETE FROM content_genre;
 DELETE FROM genre;
 INSERT INTO genre(id,name,code) SELECT id,name,old_code FROM genre_normalization_genre_backup b WHERE b.run_key=p_run_key;
 INSERT INTO content_genre(id,content_id,genre_id) SELECT id,content_id,genre_id FROM genre_normalization_relation_backup b WHERE b.run_key=p_run_key;
 INSERT INTO tmdb_genre_mapping(media_type,tmdb_genre_id,genre_id)
 SELECT media_type,tmdb_genre_id,genre_id FROM genre_normalization_tmdb_backup b WHERE b.run_key=p_run_key;
 UPDATE genre_normalization_run r SET r.status='RESTORED' WHERE r.run_key=p_run_key;
 COMMIT;
END$$
DELIMITER ;

-- BACKUP: originals and schema definitions are committed once, without altering genre.
-- SET @genre_apply=1; SET @maintenance_confirmed=1;
-- CALL flint_genre_backup(@genre_run_key);
-- Export schema/genre/relation/TMDB backups outside RDS and verify their row counts.
-- SELECT * FROM genre_normalization_genre_backup WHERE run_key=@genre_run_key;
-- SELECT COUNT(*) FROM genre_normalization_relation_backup WHERE run_key=@genre_run_key;
-- PREPARE (DDL/backups are persistent and not transactionally reversible):
-- SET @genre_backup_exported=1;
-- CALL flint_genre_prepare(@genre_run_key);
-- PREVIEW one chunk (default ROLLBACK):
-- CALL flint_genre_chunk(@genre_run_key);
-- APPLY each chunk, repeat until next_id is NULL:
-- SET @genre_commit_chunk=1; CALL flint_genre_chunk(@genre_run_key);
-- CALL flint_genre_finish(@genre_run_key);
-- SELECT COUNT(*),COUNT(DISTINCT code) FROM genre; -- both 24
-- SELECT COUNT(*) FROM tmdb_genre_mapping; -- 35
-- SELECT cg.id FROM content_genre cg LEFT JOIN genre g ON g.id=cg.genre_id WHERE g.id IS NULL;
-- SELECT content_id,genre_id,COUNT(*) FROM content_genre GROUP BY content_id,genre_id HAVING COUNT(*)>1;
-- Restore only while all writers are still stopped; this restores ALL backed-up genre relations.
-- CALL flint_genre_restore(@genre_run_key);
-- Recheck backup-vs-live counts/IDs. Keep maintenance on; restoration never starts an API image.

-- Search FULLTEXT stage, execute separately AFTER the Java backfill, in maintenance.
-- Existing indexes named by native SQL must remain until its API image is stopped.
-- CREATE TABLE content_search_stopword(value VARCHAR(30)) ENGINE=InnoDB;
-- SELECT COUNT(*) FROM content_search_stopword; -- must be zero
-- SET @old_user_stopwords=@@SESSION.innodb_ft_user_stopword_table;
-- SET SESSION innodb_ft_user_stopword_table='flint/content_search_stopword';
-- WARNING: On MySQL 8.4.8, rebuilding only search_title (even with separate DROP/ADD)
-- retains the original table-level stopword configuration when title FULLTEXT exists.
-- A successful DDL is NOT proof that the new stopwords are effective: verify a partial
-- "it" match on an existing title such as It Follows, excluding exact-title matching.
-- Additional operator approval is required for this COPY table rebuild. It keeps
-- every column/index/FK definition and all content/user links, but rebuilds ALL indexes
-- and both FULLTEXT indexes use the empty stopwords. Check storage and stop API first.
-- ALTER TABLE content FORCE, ALGORITHM=COPY, LOCK=SHARED;
-- SET SESSION innodb_ft_user_stopword_table=@old_user_stopwords;
-- Recheck source checksums, every index definition, FK links and matching through a
-- fresh connection. Legacy-title rollback restores source selection, not old stopwords.
-- https://bugs.mysql.com/bug.php?id=109885 (older verified report; 8.4.8 reproduction in tests)
-- No SET GLOBAL, no foreign_key_checks=0, no automatic COMMIT for relation preview.
