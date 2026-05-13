package kr.flint.batch.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import kr.flint.content.domain.MediaType;
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
			SELECT title, `year`, author, description, poster, bookmark_count
			FROM content
			WHERE tmdb_id = 100 AND media_type = 'MOVIE'
			""");

		assertThat(content.get("title")).isEqualTo("Oldboy");
		assertThat(((Number)content.get("year")).intValue()).isEqualTo(2003);
		assertThat(content.get("author")).isEqualTo("Park Chan-wook");
		assertThat(content.get("description")).isEqualTo("description");
		assertThat(content.get("poster")).isEqualTo("poster");
		assertThat(((Number)content.get("bookmark_count")).intValue()).isZero();
		assertThat(count("genre")).isEqualTo(2);
		assertThat(count("content_genre")).isEqualTo(2);
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
			SELECT title, `year`, author, description, poster, bookmark_count, created_at
			FROM content
			WHERE id = ?
			""", contentId);

		assertThat(content.get("title")).isEqualTo("Third");
		assertThat(((Number)content.get("year")).intValue()).isEqualTo(2026);
		assertThat(content.get("author")).isEqualTo("Creator 3");
		assertThat(content.get("description")).isEqualTo("third");
		assertThat(content.get("poster")).isEqualTo("third-poster");
		assertThat(((Number)content.get("bookmark_count")).intValue()).isEqualTo(7);
		assertThat(content.get("created_at")).isEqualTo(createdAt);
		assertThat(count("content")).isEqualTo(1);
		assertThat(count("genre")).isEqualTo(3);
		assertThat(count("content_genre")).isEqualTo(3);
	}

	private int count(String tableName) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
	}

	private void recreateSchema() {
		jdbcTemplate.execute("DROP TABLE IF EXISTS content_genre");
		jdbcTemplate.execute("DROP TABLE IF EXISTS genre");
		jdbcTemplate.execute("DROP TABLE IF EXISTS content");

		jdbcTemplate.execute("""
			CREATE TABLE content (
				id BIGINT NOT NULL PRIMARY KEY,
				tmdb_id BIGINT NOT NULL,
				media_type VARCHAR(16) NOT NULL,
				title VARCHAR(255),
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
