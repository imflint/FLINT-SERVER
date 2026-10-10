package kr.flint.batch.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.support.JobRepositoryFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import kr.flint.batch.job.refresh.ContentSearchDocumentBackfillJobConfig;
import kr.flint.batch.service.ContentSearchDocumentService;
import kr.flint.batch.service.ContentSearchDocumentService.DocumentSource;
import kr.flint.content.domain.GenreCode;

@Testcontainers
class CatalogNormalizationJdbcRepositoryTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.8")
        .withDatabaseName("flint").withUsername("test").withPassword("test");

    private SingleConnectionDataSource dataSource;
    private JdbcTemplate jdbc;
    private ContentSearchDocumentService service;
    private DataSourceTransactionManager manager;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource(DriverManager.getConnection(
            MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()), true);
        jdbc = new JdbcTemplate(dataSource);
        manager = new DataSourceTransactionManager(dataSource);
        for (String routine : List.of("flint_genre_backup", "flint_genre_prepare", "flint_genre_chunk", "flint_genre_finish", "flint_genre_restore")) {
            jdbc.execute("DROP PROCEDURE IF EXISTS " + routine);
        }
        for (String table : List.of("tmdb_genre_mapping", "content_genre", "genre", "genre_normalization_relation_backup",
            "genre_normalization_genre_backup", "genre_normalization_tmdb_backup", "genre_normalization_schema_backup",
            "genre_normalization_run", "content", "tmdb_sync_lock")) {
            jdbc.execute("DROP TABLE IF EXISTS " + table);
        }
        jdbc.execute("""
            CREATE TABLE content(id BIGINT PRIMARY KEY, tmdb_id BIGINT, media_type VARCHAR(16), title VARCHAR(255),
            title_ko VARCHAR(255),title_en VARCHAR(255),normalized_title_ko VARCHAR(255),normalized_title_en VARCHAR(255),
            search_title TEXT, `year` INT DEFAULT 2020, bookmark_count INT DEFAULT 0,
            title_dedup_key VARBINARY(1020) AS(CAST(LOWER(TRIM(title)) AS BINARY)) STORED,
            KEY idx_content_title_dedup(media_type,`year`,title_dedup_key)) ENGINE=InnoDB
            """);
        jdbc.execute("CREATE TABLE tmdb_sync_lock(lock_name VARCHAR(64) PRIMARY KEY) ENGINE=InnoDB");
        jdbc.update("INSERT INTO tmdb_sync_lock VALUES('TMDB_CONTENT_WRITE')");
        jdbc.execute("CREATE TABLE genre(id BIGINT PRIMARY KEY,name VARCHAR(255) UNIQUE NOT NULL) ENGINE=InnoDB");
        jdbc.execute("""
            CREATE TABLE content_genre(id BIGINT PRIMARY KEY,content_id BIGINT NOT NULL,genre_id BIGINT NOT NULL,
            UNIQUE KEY uk_content_genre(content_id,genre_id),FOREIGN KEY(genre_id) REFERENCES genre(id),
            FOREIGN KEY(content_id) REFERENCES content(id)) ENGINE=InnoDB
            """);
        service = new ContentSearchDocumentService(jdbc,
            new TmdbContentAdmissionJdbcRepository(jdbc, new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(dataSource)));
    }

    @AfterEach
    void close() {
        dataSource.destroy();
    }

    @Test
    void manualGenreMigrationPreviewsResumesAndPreservesCanonicalIds() throws Exception {
        for (GenreCode code : GenreCode.values()) {
            jdbc.update("INSERT INTO genre VALUES(?,?)",100L+code.ordinal(),code.displayName());
        }
        jdbc.update("INSERT INTO genre VALUES(1,'Action & Adventure')");
        for (long id=1;id<=501;id++) {
            insertContent(id);
            jdbc.update("INSERT INTO content_genre VALUES(?,?,1)",id,id);
        }
        jdbc.update("INSERT INTO content_genre VALUES(900,1,100)");
        executeManualDefinitions();
        assertThatThrownBy(() -> jdbc.execute("CALL flint_genre_prepare('test')")).hasMessageContaining("approval");
        jdbc.execute("SET @genre_apply=1");
        jdbc.execute("SET @maintenance_confirmed=1");
        jdbc.execute("SET @expected_database=NULL");
        assertThatThrownBy(() -> jdbc.execute("CALL flint_genre_prepare('test')")).hasMessageContaining("database");
        jdbc.execute("SET @expected_database='flint'");
        jdbc.execute("SET @genre_backup_exported=1");
        jdbc.execute("CALL flint_genre_prepare('test')");
        jdbc.execute("CALL flint_genre_chunk('test')");
        assertThat(count("content_genre")).isEqualTo(502);
        assertThat(jdbc.queryForObject("SELECT last_content_id FROM genre_normalization_run",Long.class)).isZero();
        jdbc.execute("SET @genre_commit_chunk=1");
        jdbc.execute("CALL flint_genre_chunk('test')");
        assertThat(jdbc.queryForObject("SELECT last_content_id FROM genre_normalization_run",Long.class)).isEqualTo(500);
        assertThatThrownBy(() -> jdbc.execute("CALL flint_genre_finish('test')")).hasMessageContaining("Chunks not finished");
        jdbc.execute("CALL flint_genre_chunk('test')");
        jdbc.execute("CALL flint_genre_finish('test')");
        assertThat(count("genre")).isEqualTo(24);
        assertThat(count("tmdb_genre_mapping")).isEqualTo(35);
        assertThat(count("content_genre")).isEqualTo(501);
        assertThat(jdbc.queryForObject("SELECT genre_id FROM content_genre WHERE content_id=1",Long.class)).isEqualTo(100);
        assertThat(jdbc.queryForObject("SELECT id FROM content_genre WHERE content_id=1",Long.class)).isEqualTo(900);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO tmdb_genre_mapping VALUES('MOVIE',90000,99999)"))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        jdbc.execute("CALL flint_genre_restore('test')");
        assertThat(count("genre")).isEqualTo(25);
        assertThat(count("content_genre")).isEqualTo(502);
        assertThat(count("tmdb_genre_mapping")).isZero();
        assertThat(jdbc.queryForObject("SELECT name FROM genre WHERE id=1",String.class)).isEqualTo("Action & Adventure");
    }

    @Test
    void backupOnlyRunCanRestoreWithoutMappingTableOrReplacingOriginalSnapshot() throws Exception {
        for (GenreCode code : GenreCode.values()) {
            jdbc.update("INSERT INTO genre VALUES(?,?)", 100L + code.ordinal(), code.displayName());
        }
        executeManualDefinitions();
        jdbc.execute("SET @genre_apply=1");
        jdbc.execute("SET @maintenance_confirmed=1");
        jdbc.execute("SET @genre_commit_chunk=1");
        jdbc.execute("CALL flint_genre_backup('early')");
        jdbc.execute("CALL flint_genre_backup('early')");
        jdbc.execute("CALL flint_genre_restore('early')");
        assertThat(count("genre")).isEqualTo(24);
        assertThat(count("genre_normalization_genre_backup")).isEqualTo(24);
        assertThat(count("tmdb_genre_mapping")).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM genre_normalization_run", String.class)).isEqualTo("RESTORED");
        assertThatThrownBy(() -> jdbc.execute("CALL flint_genre_backup('early')")).hasMessageContaining("new run key");
    }

    @Test
    void enumCodesAreBackedUpBeforeConversionAndFailedStagesResumeWithoutReplacingSnapshots() throws Exception {
        List<String> names = List.of("action, action & adventure", "adventure", "animation", "comedy", "crime",
            "documentary", "drama", "family", "fantasy", "history", "horror", "music", "mystery", "romance",
            "Science Fiction, Sci-fi & Fantasy", "TV Movie", "Thriller", "War, War & Politics", "Kids", "Action",
            "Science Fiction", "War", "Western", "Sci-Fi & Fantasy", "Reality", "Action & Adventure", "War & Politics",
            "코미디", "가족", "로맨스", "드라마", "범죄", "스릴러", "액션", "다큐멘터리", "SF", "모험",
            "애니메이션", "미스터리", "공포", "판타지", "전쟁", "음악", "서부", "역사", "TV 영화", "Talk", "Soap", "News");
        for (int i=0;i<names.size();i++) jdbc.update("INSERT INTO genre VALUES(?,?)",i+1,names.get(i));
        String members = java.util.Arrays.stream(GenreCode.values()).map(Enum::name)
            .collect(java.util.stream.Collectors.joining("','"));
        jdbc.execute("ALTER TABLE genre ADD code ENUM('"+members+"') NOT NULL, ADD INDEX idx_genre_name_audit(name)");
        jdbc.execute("""
            CREATE TABLE tmdb_genre_mapping(media_type VARCHAR(16),tmdb_genre_id BIGINT,genre_id BIGINT,
            PRIMARY KEY(media_type,tmdb_genre_id),FOREIGN KEY(genre_id) REFERENCES genre(id)) ENGINE=InnoDB
            """);
        jdbc.update("INSERT INTO tmdb_genre_mapping VALUES('MOVIE',18,7)");
        for (long id=1;id<=501;id++) {
            insertContent(id);
            jdbc.update("INSERT INTO content_genre VALUES(?,?,7)",id,id);
        }
        jdbc.update("INSERT INTO content_genre VALUES(900,1,31)");
        executeManualDefinitions();
        jdbc.execute("SET @genre_apply=1");
        jdbc.execute("SET @maintenance_confirmed=1");
        jdbc.execute("CALL flint_genre_backup('enum')");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM genre WHERE code='ACTION'",Long.class)).isEqualTo(49);
        assertThat(stage()).isEqualTo("BACKED_UP");
        assertThatThrownBy(() -> jdbc.execute("CALL flint_genre_prepare('enum')")).hasMessageContaining("Export");
        jdbc.execute("SET @genre_backup_exported=1");
        jdbc.execute("ALTER TABLE genre ADD CONSTRAINT chk_prepare_fail CHECK(code IS NULL OR code='ACTION')");
        assertThatThrownBy(() -> jdbc.execute("CALL flint_genre_prepare('enum')"))
            .hasRootCauseInstanceOf(java.sql.SQLException.class)
            .satisfies(error -> assertThat(((java.sql.SQLException) error.getCause()).getErrorCode()).isEqualTo(3819));
        assertThat(stage()).isEqualTo("BACKED_UP");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM genre WHERE code='ACTION'",Long.class)).isEqualTo(49);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM genre_normalization_genre_backup WHERE old_code='ACTION'",Long.class))
            .isEqualTo(49);
        jdbc.execute("ALTER TABLE genre DROP CHECK chk_prepare_fail");
        jdbc.execute("CALL flint_genre_prepare('enum')");
        jdbc.execute("CALL flint_genre_prepare('enum')");
        assertThat(stage()).isEqualTo("PREPARED");
        jdbc.execute("SET @genre_commit_chunk=1");
        jdbc.execute("CALL flint_genre_chunk('enum')");
        jdbc.execute("CALL flint_genre_chunk('enum')");
        assertThat(stage()).isEqualTo("RELATIONS_DONE");
        jdbc.execute("CREATE INDEX uk_genre_code ON genre(name)");
        assertThatThrownBy(() -> jdbc.execute("CALL flint_genre_finish('enum')")).hasMessageContaining("definition differs");
        assertThat(stage()).isEqualTo("FINALIZING");
        jdbc.execute("DROP INDEX uk_genre_code ON genre");
        jdbc.execute("CALL flint_genre_finish('enum')");
        jdbc.execute("CALL flint_genre_finish('enum')");
        assertThat(stage()).isEqualTo("COMPLETED");
        assertThat(count("genre")).isEqualTo(24);
        assertThat(count("tmdb_genre_mapping")).isEqualTo(35);
        assertThat(jdbc.queryForObject("SELECT genre_id FROM content_genre WHERE content_id=1",Long.class)).isEqualTo(31);
        assertThat(jdbc.queryForObject("SELECT id FROM content_genre WHERE content_id=1",Long.class)).isEqualTo(900);
        jdbc.execute("CALL flint_genre_restore('enum')");
        assertThat(count("genre")).isEqualTo(49);
        assertThat(count("content_genre")).isEqualTo(502);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM genre WHERE code='ACTION'",Long.class)).isEqualTo(49);
        assertThat(jdbc.queryForObject("SELECT genre_id FROM tmdb_genre_mapping WHERE tmdb_genre_id=18",Long.class)).isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='genre' AND index_name='idx_genre_name_audit'",Long.class))
            .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='genre' AND index_name='uk_genre_code'",Long.class))
            .isZero();
    }

    @Test
    void symbolOnlyTitlesRemainNullableWithoutDeletingOrChangingTheirSource() {
        insertContent(1);
        jdbc.update("UPDATE content SET title_ko='🔥!?',title_en='!!!' WHERE id=1");
        service.preflight();
        new TransactionTemplate(manager).executeWithoutResult(status ->
            service.rewrite(List.of(new DocumentSource(1,"🔥!?","!!!"))));
        assertThat(jdbc.queryForMap("SELECT title_ko,title_en,normalized_title_ko,normalized_title_en,search_title,bookmark_count FROM content WHERE id=1"))
            .containsEntry("title_ko","🔥!?").containsEntry("title_en","!!!")
            .containsEntry("normalized_title_ko",null).containsEntry("normalized_title_en",null)
            .containsEntry("search_title","🔥!? !!!").containsEntry("bookmark_count",7);
    }

    @Test
    void existingFullTextStopwordsRequireRebuildingTheTableToRefreshBothIndexes() throws Exception {
        insertContent(1);
        jdbc.update("UPDATE content SET title='It Follows',title_ko=NULL,title_en='It Follows',normalized_title_ko=NULL,normalized_title_en='itfollows',search_title='It Follows itfollows' WHERE id=1");
        jdbc.update("INSERT INTO genre VALUES(1,'공포')");
        jdbc.update("INSERT INTO content_genre VALUES(1,1,1)");
        var original = jdbc.queryForMap("SELECT * FROM content WHERE id=1");
        jdbc.execute("CREATE FULLTEXT INDEX ft_content_title_ngram ON content(title) WITH PARSER ngram");
        jdbc.execute("CREATE FULLTEXT INDEX ft_content_search_title_ngram ON content(search_title) WITH PARSER ngram");
        assertThat(partialEnglishMatches()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM content WHERE MATCH(title) AGAINST('\"it\"' IN BOOLEAN MODE)>0",Long.class))
            .isZero();
        jdbc.execute("CREATE TABLE IF NOT EXISTS content_search_stopword(value VARCHAR(30)) ENGINE=InnoDB");
        jdbc.execute("SET SESSION innodb_ft_user_stopword_table='flint/content_search_stopword'");
        jdbc.execute("ALTER TABLE content DROP INDEX ft_content_search_title_ngram, ADD FULLTEXT INDEX ft_content_search_title_ngram(search_title) WITH PARSER ngram");
        assertThat(partialEnglishMatches()).isZero();
        jdbc.execute("ALTER TABLE content DROP INDEX ft_content_search_title_ngram");
        jdbc.execute("ALTER TABLE content ADD FULLTEXT INDEX ft_content_search_title_ngram(search_title) WITH PARSER ngram");
        assertThat(partialEnglishMatches()).isZero();
        jdbc.execute("ALTER TABLE content FORCE, ALGORITHM=COPY, LOCK=SHARED");
        assertThat(partialEnglishMatches()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM content WHERE MATCH(title) AGAINST('\"it\"' IN BOOLEAN MODE)>0",Long.class))
            .isEqualTo(1);
        jdbc.execute("SET SESSION innodb_ft_user_stopword_table=NULL");
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword());
             var statement = connection.createStatement();
             var matches = statement.executeQuery("SELECT COUNT(*) FROM content WHERE MATCH(search_title) AGAINST('\"it\"' IN BOOLEAN MODE)>0")) {
            assertThat(matches.next()).isTrue();
            assertThat(matches.getLong(1)).isEqualTo(1);
        }
        var actual = jdbc.queryForMap("SELECT * FROM content WHERE id=1");
        assertThat(actual.keySet()).isEqualTo(original.keySet());
        original.forEach((key,value) -> {
            if (value instanceof byte[] binary) assertThat((byte[])actual.get(key)).containsExactly(binary);
            else assertThat(actual.get(key)).isEqualTo(value);
        });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='content' AND index_name='ft_content_title_ngram'",Long.class))
            .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT bookmark_count FROM content WHERE id=1",Integer.class)).isEqualTo(7);
        assertThat(jdbc.queryForMap("SELECT id,content_id,genre_id FROM content_genre"))
            .containsEntry("id",1L).containsEntry("content_id",1L).containsEntry("genre_id",1L);
    }

    private long partialEnglishMatches() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM content WHERE MATCH(search_title) AGAINST('\"it\"' IN BOOLEAN MODE)>0",Long.class);
    }

    @Test
    void searchBackfillRollsBackStaleTitlesAndDoesNotAlterSourceOrCounters() {
        insertContent(1);
        insertContent(2);
        service.preflight();
        TransactionTemplate tx = new TransactionTemplate(manager);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> service.rewrite(List.of(
            new DocumentSource(1,"해리 포터!","ＨＡＲＲＹ ＰＯＴＴＥＲ"),new DocumentSource(2,"stale",null)))))
            .hasMessageContaining("Title changed");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM content WHERE search_title='old'",Long.class)).isEqualTo(2);
        tx.executeWithoutResult(status -> service.rewrite(List.of(new DocumentSource(1,"해리 포터!","ＨＡＲＲＹ ＰＯＴＴＥＲ"))));
        assertThat(jdbc.queryForMap("SELECT title,title_ko,title_en,normalized_title_en,bookmark_count FROM content WHERE id=1"))
            .containsEntry("title","original").containsEntry("title_ko","해리 포터!")
            .containsEntry("title_en","ＨＡＲＲＹ ＰＯＴＴＥＲ").containsEntry("normalized_title_en","harrypotter")
            .containsEntry("bookmark_count",7);
        jdbc.update("UPDATE content SET title_ko=NULL,title_en=NULL WHERE id=2");
        assertThatThrownBy(service::preflight).hasMessageContaining("missing localized titles=1");
    }

    @Test
    void caseOrAccentOnlySourceChangesRollBackTheEntireBackfillChunk() {
        insertContent(1);
        insertContent(2);
        jdbc.update("UPDATE content SET title_en='Café' WHERE id=2");
        DocumentSource first = new DocumentSource(1, "해리 포터!", "ＨＡＲＲＹ ＰＯＴＴＥＲ");
        DocumentSource stale = new DocumentSource(2, "해리 포터!", "Café");
        TransactionTemplate tx = new TransactionTemplate(manager);
        for (String changedTitle : List.of("CAFE", "Cafe", "Café ")) {
            jdbc.update("UPDATE content SET title_en=? WHERE id=2", changedTitle);
            assertThatThrownBy(() -> tx.executeWithoutResult(status -> service.rewrite(List.of(first, stale))))
                .hasMessageContaining("Title changed");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM content WHERE search_title='old'", Long.class))
                .isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT title_en FROM content WHERE id=2", String.class))
                .isEqualTo(changedTitle);
        }
    }

    @Test
    void searchBackfillRestartsAfterFirstCommitted500Items() throws Exception {
        for (long id=1;id<=501;id++) insertContent(id);
        new ResourceDatabasePopulator(new ClassPathResource("batch-schema-mysql.sql")).execute(dataSource);
        JobRepositoryFactoryBean factory = new JobRepositoryFactoryBean();
        factory.setDataSource(dataSource);
        factory.setTransactionManager(manager);
        factory.afterPropertiesSet();
        var repository = factory.getObject();
        TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
        launcher.setJobRepository(repository);
        launcher.afterPropertiesSet();
        var parameters = new JobParametersBuilder().addString("backfillVersion","test-1").toJobParameters();
        jdbc.execute("ALTER TABLE content ADD CONSTRAINT chk_fail_backfill CHECK(id<>501 OR search_title='old')");
        var config = new ContentSearchDocumentBackfillJobConfig();
        var reader = config.contentSearchDocumentReader(dataSource);
        reader.afterPropertiesSet();
        var step = config.contentSearchDocumentBackfillStep(repository,manager,reader,service);
        assertThat(launcher.run(config.job(repository,step),parameters).getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM content WHERE search_title<>'old'",Long.class)).isEqualTo(500);
        jdbc.execute("ALTER TABLE content DROP CHECK chk_fail_backfill");
        var restartedReader = config.contentSearchDocumentReader(dataSource);
        restartedReader.afterPropertiesSet();
        var restartedStep = config.contentSearchDocumentBackfillStep(repository,manager,restartedReader,service);
        assertThat(launcher.run(config.job(repository,restartedStep),parameters).getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM content WHERE search_title<>'old'",Long.class)).isEqualTo(501);
    }

    private void insertContent(long id) {
        jdbc.update("""
            INSERT INTO content(id,tmdb_id,media_type,title,title_ko,title_en,search_title,bookmark_count)
            VALUES(?,?,'MOVIE','original','해리 포터!','ＨＡＲＲＹ ＰＯＴＴＥＲ','old',7)
            """,id,id);
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table,Long.class);
    }

    private String stage() {
        return jdbc.queryForObject("SELECT status FROM genre_normalization_run",String.class);
    }

    private void executeManualDefinitions() throws Exception {
        Path path = Path.of("../../docs/content-search-genre-normalization.sql");
        if (!Files.exists(path)) path=Path.of("docs/content-search-genre-normalization.sql");
        String delimiter=";";
        StringBuilder sql=new StringBuilder();
        for (String line : Files.readAllLines(path)) {
            String trimmed=line.trim();
            if (trimmed.startsWith("--") || trimmed.isEmpty()) continue;
            if (trimmed.startsWith("DELIMITER ")) { delimiter=trimmed.substring(10).trim(); continue; }
            sql.append(line).append('\n');
            if (trimmed.endsWith(delimiter)) {
                String statement=sql.toString().trim();
                jdbc.execute(statement.substring(0,statement.length()-delimiter.length()));
                sql.setLength(0);
            }
        }
    }
}
