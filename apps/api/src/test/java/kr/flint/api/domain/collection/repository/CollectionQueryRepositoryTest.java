package kr.flint.api.domain.collection.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.persistence.EntityManager;
import kr.flint.api.domain.collection.dto.response.GetCollectionDetailRes;
import kr.flint.bookmark.domain.ContentBookmark;
import kr.flint.collection.domain.Collection;
import kr.flint.collection.domain.CollectionContent;
import kr.flint.content.domain.Content;
import kr.flint.content.domain.MediaType;
import kr.flint.shared.config.QueryDslConfig;

@DataJpaTest
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EntityScan(basePackageClasses = {Collection.class, Content.class, ContentBookmark.class})
@Import({CollectionQueryRepository.class, QueryDslConfig.class})
@Sql(
	statements = {
		"DELETE FROM collection_content_images",
		"DELETE FROM collection_content",
		"DELETE FROM content_bookmark",
		"DELETE FROM content",
		"DELETE FROM collection"
	},
	config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED),
	executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class CollectionQueryRepositoryTest {

	@Container
	static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
		.withDatabaseName("flint_test")
		.withUsername("flint")
		.withPassword("flint");

	private final EntityManager entityManager;
	private final CollectionQueryRepository collectionQueryRepository;

	@Autowired
	CollectionQueryRepositoryTest(
		EntityManager entityManager,
		CollectionQueryRepository collectionQueryRepository
	) {
		this.entityManager = entityManager;
		this.collectionQueryRepository = collectionQueryRepository;
	}

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
		registry.add("spring.datasource.username", MYSQL::getUsername);
		registry.add("spring.datasource.password", MYSQL::getPassword);
		registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
		registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MySQLDialect");
	}

	@Test
	@DisplayName("컬렉션 상세 콘텐츠 목록은 sortOrder 순서대로 조회")
	void getContentListOrdersBySortOrder() {
		// given
		Collection collection = persistCollection();
		Content first = persistContent(1001L, "첫 번째");
		Content second = persistContent(1002L, "두 번째");
		Content third = persistContent(1003L, "세 번째");
		persistCollectionContent(collection, third, "세 번째 이유", 2);
		persistCollectionContent(collection, first, "첫 번째 이유", 0);
		persistCollectionContent(collection, second, "두 번째 이유", 1);
		entityManager.flush();
		entityManager.clear();

		// when
		List<GetCollectionDetailRes.Content> results =
			collectionQueryRepository.getContentList(collection.getId(), 1L);

		// then
		assertThat(results)
			.extracting(GetCollectionDetailRes.Content::title)
			.containsExactly("첫 번째", "두 번째", "세 번째");
	}

	@Test
	@DisplayName("같은 컬렉션 안에서 sortOrder는 중복 저장할 수 없음")
	void collectionContentSortOrderIsUniqueInCollection() {
		// given
		Collection collection = persistCollection();
		Content first = persistContent(2001L, "첫 번째");
		Content second = persistContent(2002L, "두 번째");
		persistCollectionContent(collection, first, "첫 번째 이유", 0);
		persistCollectionContent(collection, second, "두 번째 이유", 0);

		// when & then
		assertThatThrownBy(entityManager::flush)
			.hasRootCauseInstanceOf(SQLIntegrityConstraintViolationException.class);
	}

	private Collection persistCollection() {
		Collection collection = Collection.create("컬렉션", "설명", "image.jpg", true, 1L);
		entityManager.persist(collection);
		return collection;
	}

	private Content persistContent(Long tmdbId, String title) {
		Content content = Content.create(
			tmdbId,
			MediaType.MOVIE,
			title,
			2026,
			"감독",
			"설명",
			"poster.jpg"
		);
		entityManager.persist(content);
		return content;
	}

	private void persistCollectionContent(Collection collection, Content content, String reason, int sortOrder) {
		entityManager.persist(CollectionContent.create(collection, content.getId(), false, reason, sortOrder));
	}
}
