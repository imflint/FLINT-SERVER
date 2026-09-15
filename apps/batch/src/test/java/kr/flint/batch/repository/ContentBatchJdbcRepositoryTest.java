package kr.flint.batch.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import kr.flint.content.domain.MediaType;
import kr.flint.content.dto.ContentCatalogStatus;
import kr.flint.content.dto.ContentUpsertCommand;

@Testcontainers(disabledWithoutDocker = true)
class ContentBatchJdbcRepositoryTest {

	@Container
	private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
		.withDatabaseName("flint")
		.withUsername("test")
		.withPassword("test");

	private JdbcTemplate jdbcTemplate;
	private ContentBatchJdbcRepository repository;
	private TmdbCatalogEntryJdbcRepository catalogEntryRepository;

	@BeforeEach
	void setUp() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setDriverClassName(MYSQL.getDriverClassName());
		dataSource.setUrl(MYSQL.getJdbcUrl());
		dataSource.setUsername(MYSQL.getUsername());
		dataSource.setPassword(MYSQL.getPassword());

		jdbcTemplate = new JdbcTemplate(dataSource);
		repository = new ContentBatchJdbcRepository(
			jdbcTemplate,
			new NamedParameterJdbcTemplate(dataSource)
		);
		catalogEntryRepository = new TmdbCatalogEntryJdbcRepository(
			jdbcTemplate,
			new NamedParameterJdbcTemplate(dataSource)
		);

		recreateSchema();
	}

	@Test
	void upsertAllInsertsContentGenresAndLinks() {
		repository.upsertAll(List.of(ContentUpsertCommand.of(
			100L,
			MediaType.MOVIE,
			"Oldboy",
			2003,
			"Park Chan-wook",
			"description",
			"poster",
			List.of("Drama", "Thriller", "Drama")
		)));

		Map<String, Object> content = jdbcTemplate.queryForMap("""
			SELECT title, title_ko, title_en, `year`, author, description, poster, bookmark_count
			FROM content
			WHERE tmdb_id = 100 AND media_type = 'MOVIE'
			""");

		assertThat(content.get("title")).isEqualTo("Oldboy");
		assertThat(content.get("title_ko")).isEqualTo("Oldboy");
		assertThat(content.get("title_en")).isNull();
		assertThat(((Number)content.get("year")).intValue()).isEqualTo(2003);
		assertThat(content.get("author")).isEqualTo("Park Chan-wook");
		assertThat(content.get("description")).isEqualTo("description");
		assertThat(content.get("poster")).isEqualTo("poster");
		assertThat(((Number)content.get("bookmark_count")).intValue()).isZero();
		assertThat(count("genre")).isEqualTo(2);
		assertThat(count("content_genre")).isEqualTo(2);
		Map<String, Object> registry = jdbcTemplate.queryForMap("""
			SELECT status, title_ko, title_en, normalized_title_ko, search_title
			FROM tmdb_catalog_entry
			WHERE tmdb_id = 100 AND media_type = 'MOVIE'
			""");
		assertThat(registry.get("status")).isEqualTo("SYNCED");
		assertThat(registry.get("title_ko")).isEqualTo("Oldboy");
		assertThat(registry.get("title_en")).isNull();
		assertThat(registry.get("normalized_title_ko")).isEqualTo("oldboy");
		assertThat(registry.get("search_title")).isEqualTo("Oldboy oldboy");
	}

	@Test
	void upsertAllUpdatesExistingContentAndKeepsCounters() {
		repository.upsertAll(List.of(ContentUpsertCommand.of(
			200L,
			MediaType.TV,
			"First",
			2024,
			"Creator",
			"first",
			"first-poster",
			List.of("Drama")
		)));

		Long contentId = jdbcTemplate.queryForObject("SELECT id FROM content WHERE tmdb_id = 200", Long.class);
		Timestamp createdAt = jdbcTemplate.queryForObject(
			"SELECT created_at FROM content WHERE id = ?",
			Timestamp.class,
			contentId
		);
		jdbcTemplate.update("UPDATE content SET bookmark_count = 7 WHERE id = ?", contentId);

		repository.upsertAll(List.of(
			ContentUpsertCommand.of(
				200L,
				MediaType.TV,
				"Second",
				2025,
				"Creator 2",
				"second",
				"second-poster",
				List.of("Comedy")
			),
			ContentUpsertCommand.of(
				200L,
				MediaType.TV,
				"Third",
				2026,
				"Creator 3",
				"third",
				"third-poster",
				List.of("Drama", "Sci-Fi")
			)
		));

		Map<String, Object> content = jdbcTemplate.queryForMap("""
			SELECT title, `year`, author, description, poster, bookmark_count
			FROM content
			WHERE id = ?
			""", contentId);
		Timestamp updatedCreatedAt = jdbcTemplate.queryForObject(
			"SELECT created_at FROM content WHERE id = ?",
			Timestamp.class,
			contentId
		);

		assertThat(content.get("title")).isEqualTo("Third");
		assertThat(((Number)content.get("year")).intValue()).isEqualTo(2026);
		assertThat(content.get("author")).isEqualTo("Creator 3");
		assertThat(content.get("description")).isEqualTo("third");
		assertThat(content.get("poster")).isEqualTo("third-poster");
		assertThat(((Number)content.get("bookmark_count")).intValue()).isEqualTo(7);
		assertThat(updatedCreatedAt).isEqualTo(createdAt);
		assertThat(count("content")).isEqualTo(1);
		assertThat(count("genre")).isEqualTo(3);
		assertThat(count("content_genre")).isEqualTo(3);
	}

	@Test
	void exportRegistrySelectsOnlyNewRetryAndDueEntries() {
		LocalDate exportDate = LocalDate.of(2026, 9, 12);
		jdbcTemplate.update("""
			INSERT INTO tmdb_catalog_entry (
				id, media_type, tmdb_id, status, next_refresh_at, created_at, updated_at
			) VALUES
				(1, 'MOVIE', 101, 'SYNCED', DATE_ADD(UTC_TIMESTAMP(), INTERVAL 2 DAY), UTC_TIMESTAMP(), UTC_TIMESTAMP()),
				(2, 'MOVIE', 102, 'SYNCED', DATE_SUB(UTC_TIMESTAMP(), INTERVAL 1 DAY), UTC_TIMESTAMP(), UTC_TIMESTAMP()),
				(3, 'MOVIE', 103, 'RETRY', NULL, UTC_TIMESTAMP(), UTC_TIMESTAMP()),
				(4, 'MOVIE', 104, 'INELIGIBLE_LANGUAGE', NULL, UTC_TIMESTAMP(), UTC_TIMESTAMP())
			""");

		List<kr.flint.batch.job.TmdbIdLine> selected = catalogEntryRepository.registerExportBatch(
			MediaType.MOVIE,
			exportDate,
			List.of(
				new kr.flint.batch.job.TmdbIdLine(101L, null, null, null),
				new kr.flint.batch.job.TmdbIdLine(102L, null, null, null),
				new kr.flint.batch.job.TmdbIdLine(103L, null, null, null),
				new kr.flint.batch.job.TmdbIdLine(104L, null, null, null),
				new kr.flint.batch.job.TmdbIdLine(105L, null, null, null)
			)
		);

		assertThat(selected).extracting(kr.flint.batch.job.TmdbIdLine::id)
			.containsExactly(102L, 103L, 105L);
		assertThat(jdbcTemplate.queryForObject(
			"SELECT last_seen_export_date FROM tmdb_catalog_entry WHERE tmdb_id = 101",
			LocalDate.class
		)).isEqualTo(exportDate);
		assertThat(jdbcTemplate.queryForObject(
			"SELECT status FROM tmdb_catalog_entry WHERE tmdb_id = 105",
			String.class
		)).isEqualTo("PENDING");
	}

	@Test
	void classifyOnlyUpdatesRegistryWithoutMutatingExistingContent() {
		repository.upsertAll(List.of(ContentUpsertCommand.localized(
			300L,
			MediaType.MOVIE,
			"기존 제목",
			"Existing title",
			2026,
			"Director",
			"description",
			"poster",
			List.of("Drama")
		)));

		repository.classifyAll(List.of(ContentUpsertCommand.classified(
			300L,
			MediaType.MOVIE,
			ContentCatalogStatus.INELIGIBLE_LANGUAGE,
			null
		)));

		assertThat(jdbcTemplate.queryForObject(
			"SELECT title FROM content WHERE tmdb_id = 300 AND media_type = 'MOVIE'",
			String.class
		)).isEqualTo("기존 제목");
		assertThat(jdbcTemplate.queryForObject(
			"SELECT status FROM tmdb_catalog_entry WHERE tmdb_id = 300 AND media_type = 'MOVIE'",
			String.class
		)).isEqualTo("INELIGIBLE_LANGUAGE");
	}

	private int count(String tableName) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
	}

	private void recreateSchema() {
		jdbcTemplate.execute("DROP TABLE IF EXISTS content_genre");
		jdbcTemplate.execute("DROP TABLE IF EXISTS genre");
		jdbcTemplate.execute("DROP TABLE IF EXISTS content");
		jdbcTemplate.execute("DROP TABLE IF EXISTS tmdb_catalog_entry");

		jdbcTemplate.execute("""
			CREATE TABLE tmdb_catalog_entry (
				id BIGINT NOT NULL PRIMARY KEY,
				media_type VARCHAR(16) NOT NULL,
				tmdb_id BIGINT NOT NULL,
				status VARCHAR(32) NOT NULL,
				title_ko VARCHAR(255),
				title_en VARCHAR(255),
				normalized_title_ko VARCHAR(255),
				normalized_title_en VARCHAR(255),
				search_title TEXT,
				last_seen_export_date DATE,
				last_synced_at DATETIME(6),
				next_refresh_at DATETIME(6),
				error_message VARCHAR(1000),
				created_at DATETIME(6) NOT NULL,
				updated_at DATETIME(6) NOT NULL,
				UNIQUE KEY uk_tmdb_catalog_media_tmdb (media_type, tmdb_id)
			)
			""");

		jdbcTemplate.execute("""
			CREATE TABLE content (
				id BIGINT NOT NULL PRIMARY KEY,
				tmdb_id BIGINT NOT NULL,
				media_type VARCHAR(16) NOT NULL,
				title VARCHAR(255),
				title_ko VARCHAR(255),
				title_en VARCHAR(255),
				normalized_title_ko VARCHAR(255),
				normalized_title_en VARCHAR(255),
				search_title TEXT,
				`year` INT,
				author VARCHAR(255),
				description TEXT,
				poster VARCHAR(255),
				bookmark_count INT,
				created_at DATETIME(6),
				updated_at DATETIME(6),
				UNIQUE KEY uk_content_tmdb (tmdb_id, media_type)
			)
			""");
		jdbcTemplate.execute("""
			CREATE TABLE genre (
				id BIGINT NOT NULL PRIMARY KEY,
				name VARCHAR(255) NOT NULL,
				UNIQUE KEY uk_genre_name (name)
			)
			""");
		jdbcTemplate.execute("""
			CREATE TABLE content_genre (
				id BIGINT NOT NULL PRIMARY KEY,
				content_id BIGINT NOT NULL,
				genre_id BIGINT NOT NULL,
				UNIQUE KEY uk_content_genre (content_id, genre_id)
			)
			""");
	}
}
