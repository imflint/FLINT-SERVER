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
        for (String routine : List.of("flint_genre_prepare", "flint_genre_chunk", "flint_genre_finish", "flint_genre_restore")) {
            jdbc.execute("DROP PROCEDURE IF EXISTS " + routine);
        }
        for (String table : List.of("tmdb_genre_mapping", "content_genre", "genre", "genre_normalization_relation_backup",
            "genre_normalization_genre_backup", "genre_normalization_tmdb_backup", "genre_normalization_run", "content", "tmdb_sync_lock")) {
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
