package kr.flint.batch.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

@Testcontainers
class CatalogNormalizationJdbcRepositoryTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.8")
        .withDatabaseName("flint").withUsername("test").withPassword("test")
        .withCommand("--log-bin-trust-function-creators=1");

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
        for (String table : List.of("content_genre", "genre", "content_search_document", "content", "tmdb_sync_lock")) {
            jdbc.execute("DROP TABLE IF EXISTS " + table);
        }
        jdbc.execute("""
            CREATE TABLE content(id BIGINT PRIMARY KEY, tmdb_id BIGINT, media_type VARCHAR(16), title VARCHAR(255),
            title_ko VARCHAR(255),title_en VARCHAR(255),normalized_title_ko VARCHAR(255),normalized_title_en VARCHAR(255),
            search_title TEXT, word_title_ko TEXT, word_title_en TEXT, `year` INT DEFAULT 2020, bookmark_count INT DEFAULT 0,
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
        new TransactionTemplate(manager).executeWithoutResult(status ->
            service.rewriteWords(List.of(new DocumentSource(1,"🔥!?","!!!"))));
        assertThat(jdbc.queryForMap("SELECT word_title_ko,word_title_en,search_title,bookmark_count FROM content WHERE id=1"))
            .containsEntry("word_title_ko",null).containsEntry("word_title_en",null)
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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void searchBackfillRestartsAfterFirstCommitted500Items(boolean wordsOnly) throws Exception {
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
        var parameters = new JobParametersBuilder().addString("backfillVersion", wordsOnly ? "test-words-1" : "test-1").toJobParameters();
        jdbc.execute("ALTER TABLE content ADD CONSTRAINT chk_fail_backfill CHECK(id<>501 OR "
            + (wordsOnly ? "word_title_en IS NULL" : "search_title='old'") + ")");
        var config = new ContentSearchDocumentBackfillJobConfig();
        org.springframework.test.util.ReflectionTestUtils.setField(config, "wordsOnly", wordsOnly);
        var reader = config.contentSearchDocumentReader(dataSource);
        reader.afterPropertiesSet();
        var step = config.contentSearchDocumentBackfillStep(repository,manager,reader,service);
        assertThat(launcher.run(config.job(repository,step),parameters).getStatus()).isEqualTo(BatchStatus.FAILED);
        String changed = wordsOnly ? "word_title_en IS NOT NULL" : "search_title<>'old'";
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM content WHERE " + changed,Long.class)).isEqualTo(500);
        jdbc.execute("ALTER TABLE content DROP CHECK chk_fail_backfill");
        var restartedReader = config.contentSearchDocumentReader(dataSource);
        restartedReader.afterPropertiesSet();
        var restartedStep = config.contentSearchDocumentBackfillStep(repository,manager,restartedReader,service);
        assertThat(launcher.run(config.job(repository,restartedStep),parameters).getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM content WHERE " + changed,Long.class)).isEqualTo(501);
        if (wordsOnly) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM content WHERE search_title='old' AND bookmark_count=7",Long.class)).isEqualTo(501);
        }
    }

    private void insertContent(long id) {
        jdbc.update("""
            INSERT INTO content(id,tmdb_id,media_type,title,title_ko,title_en,search_title,bookmark_count)
            VALUES(?,?,'MOVIE','original','해리 포터!','ＨＡＲＲＹ ＰＯＴＴＥＲ','old',7)
            """,id,id);
    }

    @Test
    void wordOnlyBackfillPreservesExistingDocumentsAndRollsBackStaleChunks() {
        insertContent(1);
        insertContent(2);
        var tx = new TransactionTemplate(manager);
        var source = new DocumentSource(1, "해리 포터!", "ＨＡＲＲＹ ＰＯＴＴＥＲ");
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> service.rewriteWords(List.of(
            source, new DocumentSource(2, "stale", null))))).hasMessageContaining("Title changed");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM content WHERE word_title_en IS NOT NULL", Long.class)).isZero();
        tx.executeWithoutResult(status -> service.rewriteWords(List.of(source)));
        assertThat(jdbc.queryForMap("SELECT title,search_title,normalized_title_en,word_title_ko,word_title_en,bookmark_count FROM content WHERE id=1"))
            .containsEntry("title", "original").containsEntry("search_title", "old").containsEntry("normalized_title_en", null)
            .containsEntry("word_title_ko", "해리 포터").containsEntry("word_title_en", "harry potter").containsEntry("bookmark_count", 7);
        tx.executeWithoutResult(status -> service.rewriteWords(List.of(source)));
        assertThat(jdbc.queryForObject("SELECT word_title_en FROM content WHERE id=1", String.class)).isEqualTo("harry potter");
    }

}
