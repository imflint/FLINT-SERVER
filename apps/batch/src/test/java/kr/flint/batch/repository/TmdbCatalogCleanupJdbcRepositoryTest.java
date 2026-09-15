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

@Testcontainers(disabledWithoutDocker = true)
class TmdbCatalogCleanupJdbcRepositoryTest {

	@Container
	private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
		.withDatabaseName("flint")
		.withUsername("test")
		.withPassword("test");

	private JdbcTemplate jdbcTemplate;
	private TmdbCatalogCleanupJdbcRepository repository;

	@BeforeEach
	void setUp() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setDriverClassName(MYSQL.getDriverClassName());
		dataSource.setUrl(MYSQL.getJdbcUrl());
		dataSource.setUsername(MYSQL.getUsername());
		dataSource.setPassword(MYSQL.getPassword());
		jdbcTemplate = new JdbcTemplate(dataSource);
		repository = new TmdbCatalogCleanupJdbcRepository(
			jdbcTemplate,
			new NamedParameterJdbcTemplate(dataSource)
		);
		recreateSchema();
		insertFixtures();
	}

	@Test
	void deletesDependenciesQueuesOwnedImagesAndSoftDeletesEmptyCollections() {
		var manifest = repository.createManifest(List.of(1L), "hash");

		assertThat(repository.deleteNextChunk(manifest.id(), 500)).isEqualTo(1);
		assertThat(repository.promoteLocalizedTitlesForEligibleContents()).isEqualTo(1);
		repository.finalizeAffectedCollections(manifest.id());
		repository.completeManifest(manifest.id());

		assertThat(count("collection_content_images")).isZero();
		assertThat(countByContent("collection_content", 1L)).isZero();
		assertThat(countByContent("content_bookmark", 1L)).isZero();
		assertThat(countByContent("ott_content", 1L)).isZero();
		assertThat(countByContent("content_keywords", 1L)).isZero();
		assertThat(countByContent("content_genre", 1L)).isZero();
		assertThat(countById("content", 1L)).isZero();
		assertThat(countById("content", 2L)).isOne();
		assertThat(jdbcTemplate.queryForMap(
			"SELECT title, title_ko, title_en, normalized_title_ko FROM content WHERE id = 2"
		)).containsEntry("title", "한국 제목")
			.containsEntry("title_ko", "한국 제목")
			.containsEntry("title_en", "English title")
			.containsEntry("normalized_title_ko", "한국제목");
		assertThat(jdbcTemplate.queryForObject(
			"SELECT deleted_at IS NOT NULL FROM collection WHERE id = 10", Boolean.class
		)).isTrue();
		assertThat(jdbcTemplate.queryForObject(
			"SELECT sort_order FROM collection_content WHERE collection_id = 11", Integer.class
		)).isZero();
		assertThat(jdbcTemplate.queryForList(
			"SELECT object_key FROM tmdb_s3_delete_queue ORDER BY object_key", String.class
		)).containsExactly(
			"collection/content/custom.jpg",
			"collection/content/gallery.jpg"
		);
		assertThat(repository.findManifest(manifest.id()).orElseThrow().status()).isEqualTo("COMPLETED");
	}

	private int count(String table) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
	}

	private int countByContent(String table, Long contentId) {
		return jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM " + table + " WHERE content_id = ?", Integer.class, contentId
		);
	}

	private int countById(String table, Long id) {
		return jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM " + table + " WHERE id = ?", Integer.class, id
		);
	}

	private void recreateSchema() {
		List.of(
			"tmdb_s3_delete_queue", "tmdb_content_prune_collection", "tmdb_content_prune_candidate",
			"tmdb_content_prune_manifest", "tmdb_catalog_entry", "collection_content_images", "content_bookmark", "ott_content",
			"content_keywords", "content_genre", "collection_content", "collection", "content"
		).forEach(table -> jdbcTemplate.execute("DROP TABLE IF EXISTS " + table));

		jdbcTemplate.execute("""
			CREATE TABLE content (
				id BIGINT PRIMARY KEY, tmdb_id BIGINT NOT NULL, media_type VARCHAR(16) NOT NULL,
				title VARCHAR(255), title_ko VARCHAR(255), title_en VARCHAR(255),
				normalized_title_ko VARCHAR(255), normalized_title_en VARCHAR(255), search_title TEXT,
				updated_at DATETIME(6)
			)
			""");
		jdbcTemplate.execute("""
			CREATE TABLE tmdb_catalog_entry (
				id BIGINT PRIMARY KEY, tmdb_id BIGINT NOT NULL, media_type VARCHAR(16) NOT NULL,
				status VARCHAR(32) NOT NULL, title_ko VARCHAR(255), title_en VARCHAR(255),
				normalized_title_ko VARCHAR(255), normalized_title_en VARCHAR(255), search_title TEXT
			)
			""");
		jdbcTemplate.execute("""
			CREATE TABLE collection (
				id BIGINT PRIMARY KEY, deleted_at DATETIME(6), updated_at DATETIME(6)
			)
			""");
		jdbcTemplate.execute("""
			CREATE TABLE collection_content (
				id BIGINT PRIMARY KEY, collection_id BIGINT NOT NULL, content_id BIGINT NOT NULL,
				sort_order INT NOT NULL, custom_image VARCHAR(1024),
				CONSTRAINT fk_cleanup_cc_content FOREIGN KEY (content_id) REFERENCES content(id)
			)
			""");
		jdbcTemplate.execute("""
			CREATE TABLE collection_content_images (
				id BIGINT PRIMARY KEY, collection_content_id BIGINT NOT NULL, image_key VARCHAR(1024),
				CONSTRAINT fk_cleanup_image_cc FOREIGN KEY (collection_content_id) REFERENCES collection_content(id)
			)
			""");
		for (String table : List.of("content_bookmark", "ott_content", "content_keywords", "content_genre")) {
			jdbcTemplate.execute("CREATE TABLE " + table + " (id BIGINT PRIMARY KEY, content_id BIGINT NOT NULL, "
				+ "CONSTRAINT fk_" + table + "_content FOREIGN KEY (content_id) REFERENCES content(id))");
		}
		jdbcTemplate.execute("""
			CREATE TABLE tmdb_content_prune_manifest (
				id BIGINT PRIMARY KEY, status VARCHAR(16), candidate_count BIGINT, candidate_hash CHAR(64),
				processed_count BIGINT, created_at DATETIME(6), executed_at DATETIME(6)
			)
			""");
		jdbcTemplate.execute("""
			CREATE TABLE tmdb_content_prune_candidate (
				manifest_id BIGINT, content_id BIGINT, processed BOOLEAN, processed_at DATETIME(6),
				PRIMARY KEY (manifest_id, content_id)
			)
			""");
		jdbcTemplate.execute("""
			CREATE TABLE tmdb_content_prune_collection (
				manifest_id BIGINT, collection_id BIGINT, PRIMARY KEY (manifest_id, collection_id)
			)
			""");
		jdbcTemplate.execute("""
			CREATE TABLE tmdb_s3_delete_queue (
				id BIGINT PRIMARY KEY, object_key VARCHAR(1024), object_key_hash BINARY(32) UNIQUE,
				delete_after DATETIME(6),
				status VARCHAR(16), retry_count INT, next_retry_at DATETIME(6), error_message VARCHAR(1000),
				deleted_at DATETIME(6), created_at DATETIME(6), updated_at DATETIME(6)
			)
			""");
	}

	private void insertFixtures() {
		jdbcTemplate.update("""
			INSERT INTO content (id, tmdb_id, media_type, title) VALUES
				(1, 100, 'MOVIE', '삭제 작품'),
				(2, 200, 'MOVIE', 'Legacy title')
			""");
		jdbcTemplate.update("""
			INSERT INTO tmdb_catalog_entry (
				id, tmdb_id, media_type, status, title_ko, title_en,
				normalized_title_ko, normalized_title_en, search_title
			) VALUES (
				2, 200, 'MOVIE', 'SYNCED', '한국 제목', 'English title',
				'한국제목', 'englishtitle', '한국 제목 한국제목 English title englishtitle'
			)
			""");
		jdbcTemplate.update("INSERT INTO collection (id) VALUES (10), (11)");
		jdbcTemplate.update("""
			INSERT INTO collection_content (id, collection_id, content_id, sort_order, custom_image) VALUES
				(100, 10, 1, 0, 'collection/content/custom.jpg'),
				(101, 11, 1, 0, 'https://external.example/image.jpg'),
				(102, 11, 2, 1, NULL)
			""");
		jdbcTemplate.update("""
			INSERT INTO collection_content_images (id, collection_content_id, image_key) VALUES
				(1000, 100, 'collection/content/gallery.jpg'),
				(1001, 101, 'https://external.example/gallery.jpg')
			""");
		for (String table : List.of("content_bookmark", "ott_content", "content_keywords", "content_genre")) {
			jdbcTemplate.update("INSERT INTO " + table + " (id, content_id) VALUES (?, 1)", table.hashCode() & 0xffffffffL);
		}
	}
}
