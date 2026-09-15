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
	void replaceProvidersPreservesLegacyProviderIdsAndDeduplicatesRows() {
		List<OttSyncDraft> drafts = List.of(
			new OttSyncDraft(10L, "https://watch.example.com/10", List.of(
				provider(8L, "Netflix", 0),
				provider(337L, "Disney Plus", 1),
				provider(8L, "Netflix", 0)
			)),
			new OttSyncDraft(11L, "https://watch.example.com/11", List.of())
		);

		repository.replaceProviders(drafts);
		repository.replaceProviders(drafts);

		assertThat(count("ott_content")).isEqualTo(2);
		assertThat(jdbcTemplate.queryForList("""
			SELECT id
			FROM ott_provider
			WHERE tmdb_provider_id IN (8, 337)
			ORDER BY id
			""", Long.class)).containsExactly(1L, 2L);
		assertThat(jdbcTemplate.queryForList("""
			SELECT content_url
			FROM ott_content
			ORDER BY content_url
			""", String.class))
			.containsExactly("https://watch.example.com/10", "https://watch.example.com/10");
	}

	@Test
	void successfulEmptyProviderResponseRemovesExistingLinks() {
		repository.replaceProviders(List.of(
			new OttSyncDraft(10L, "https://watch.example.com/10", List.of(provider(8L, "Netflix", 0)))
		));

		repository.replaceProviders(List.of(new OttSyncDraft(10L, null, List.of())));

		assertThat(count("ott_content")).isZero();
	}

	@Test
	void providerMasterDeactivatesMissingProvidersWithoutDeletingLegacyRows() {
		repository.synchronizeProviderMaster(List.of(provider(8L, "Netflix", 1)));

		assertThat(jdbcTemplate.queryForObject(
			"SELECT active FROM ott_provider WHERE id = 1",
			Boolean.class
		)).isTrue();
		assertThat(jdbcTemplate.queryForObject(
			"SELECT active FROM ott_provider WHERE id = 2",
			Boolean.class
		)).isTrue();

		jdbcTemplate.update("UPDATE ott_provider SET tmdb_provider_id = 337 WHERE id = 2");
		repository.synchronizeProviderMaster(List.of(provider(8L, "Netflix", 1)));

		assertThat(jdbcTemplate.queryForObject(
			"SELECT active FROM ott_provider WHERE id = 2",
			Boolean.class
		)).isFalse();
		assertThat(count("ott_provider")).isEqualTo(2);
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
				url VARCHAR(255) NOT NULL,
				tmdb_provider_id BIGINT NULL,
				display_priority INT NOT NULL DEFAULT 9999,
				active BOOLEAN NOT NULL DEFAULT TRUE,
				UNIQUE KEY uk_ott_provider_tmdb_provider_id (tmdb_provider_id)
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

	private OttSyncDraft.Provider provider(Long tmdbProviderId, String name, int displayPriority) {
		return new OttSyncDraft.Provider(
			tmdbProviderId,
			name,
			"https://image.tmdb.org/t/p/original/provider.png",
			displayPriority
		);
	}
}
