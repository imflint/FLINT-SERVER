package kr.flint.batch.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import kr.flint.batch.job.ott.OttSyncDraft;

@Testcontainers(disabledWithoutDocker = true)
class OttBatchJdbcRepositoryTest {

	@Container
	private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
		.withDatabaseName("flint")
		.withUsername("test")
		.withPassword("test");

	private JdbcTemplate jdbcTemplate;
	private OttBatchJdbcRepository repository;

	@BeforeEach
	void setUp() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setDriverClassName(MYSQL.getDriverClassName());
		dataSource.setUrl(MYSQL.getJdbcUrl());
		dataSource.setUsername(MYSQL.getUsername());
		dataSource.setPassword(MYSQL.getPassword());

		jdbcTemplate = new JdbcTemplate(dataSource);
		repository = new OttBatchJdbcRepository(
			jdbcTemplate,
			new NamedParameterJdbcTemplate(dataSource)
		);

		recreateSchema();
		insertProviders();
	}

	@Test
	void linkProvidersInsertsExistingProvidersOnlyAndDeduplicatesRows() {
		List<OttSyncDraft> drafts = List.of(
			new OttSyncDraft(10L, List.of("Netflix", "Missing", "Netflix")),
			new OttSyncDraft(10L, List.of("Disney Plus")),
			new OttSyncDraft(null, List.of("Netflix")),
			new OttSyncDraft(11L, List.of())
		);

		repository.linkProviders(drafts);
		repository.linkProviders(drafts);

		assertThat(count("ott_content")).isEqualTo(2);
		assertThat(jdbcTemplate.queryForList("""
			SELECT content_url
			FROM ott_content
			ORDER BY content_url
			""", String.class))
			.containsExactly("https://disney.example.com", "https://netflix.example.com");
	}

	private int count(String tableName) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
	}

	private void recreateSchema() {
		jdbcTemplate.execute("DROP TABLE IF EXISTS ott_content");
		jdbcTemplate.execute("DROP TABLE IF EXISTS ott_provider");

		jdbcTemplate.execute("""
			CREATE TABLE ott_provider (
				id BIGINT NOT NULL PRIMARY KEY,
				name VARCHAR(255) NOT NULL,
				logo_url VARCHAR(255) NOT NULL,
				url VARCHAR(255) NOT NULL
			)
			""");
		jdbcTemplate.execute("""
			CREATE TABLE ott_content (
				id BIGINT NOT NULL PRIMARY KEY,
				content_id BIGINT NOT NULL,
				ott_provider_id BIGINT NOT NULL,
				content_url TEXT,
				UNIQUE KEY uk_content_ott (content_id, ott_provider_id)
			)
			""");
	}

	private void insertProviders() {
		jdbcTemplate.update(
			"INSERT INTO ott_provider (id, name, logo_url, url) VALUES (?, ?, ?, ?)",
			1L,
			"Netflix",
			"logo-netflix",
			"https://netflix.example.com"
		);
		jdbcTemplate.update(
			"INSERT INTO ott_provider (id, name, logo_url, url) VALUES (?, ?, ?, ?)",
			2L,
			"Disney Plus",
			"logo-disney",
			"https://disney.example.com"
		);
	}
}
