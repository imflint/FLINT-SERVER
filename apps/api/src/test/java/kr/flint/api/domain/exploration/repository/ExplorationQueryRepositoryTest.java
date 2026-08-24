package kr.flint.api.domain.exploration.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

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
import kr.flint.api.domain.exploration.repository.ExplorationQueryRepository.RepresentativeCollectionRow;
import kr.flint.collection.domain.Collection;
import kr.flint.collection.domain.CollectionContent;
import kr.flint.content.domain.Content;
import kr.flint.content.domain.MediaType;
import kr.flint.shared.config.QueryDslConfig;

@DataJpaTest
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EntityScan(basePackageClasses = {Collection.class, Content.class})
@Import({ExplorationQueryRepository.class, QueryDslConfig.class})
@Sql(
	statements = {
		"DELETE FROM collection_content_images",
		"DELETE FROM collection_content",
		"DELETE FROM content",
		"DELETE FROM collection"
	},
	config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED),
	executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class ExplorationQueryRepositoryTest {

	@Container
	static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
		.withDatabaseName("flint_test")
		.withUsername("flint")
		.withPassword("flint");

	private final EntityManager entityManager;
	private final ExplorationQueryRepository explorationQueryRepository;

	@Autowired
	ExplorationQueryRepositoryTest(
		EntityManager entityManager,
		ExplorationQueryRepository explorationQueryRepository
	) {
		this.entityManager = entityManager;
		this.explorationQueryRepository = explorationQueryRepository;
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
	@DisplayName("대표 컬렉션 ID와 사용자 선정 이유는 같은 최신 컬렉션에서 조회")
	void findRepresentativeCollectionsUsesSameLatestCollection() {
		Content content = Content.create(
			1L,
			MediaType.MOVIE,
			"작품",
			2026,
			"감독",
			"TMDB 줄거리",
			"poster.jpg"
		);
		entityManager.persist(content);
		Collection first = persistCollection("첫 번째 컬렉션");
		Collection second = persistCollection("두 번째 컬렉션");
		entityManager.flush();
		persistCollectionContent(first, content, "첫 번째 사용자가 작성한 소개");
		persistCollectionContent(second, content, "두 번째 사용자가 작성한 소개");
		entityManager.flush();
		entityManager.clear();

		Map<Long, RepresentativeCollectionRow> result =
			explorationQueryRepository.findRepresentativeCollections(java.util.List.of(content.getId()));

		Collection expected = first.getId() > second.getId() ? first : second;
		String expectedReason = expected == first
			? "첫 번째 사용자가 작성한 소개"
			: "두 번째 사용자가 작성한 소개";
		assertThat(result.get(content.getId()).collectionId()).isEqualTo(expected.getId());
		assertThat(result.get(content.getId()).reason()).isEqualTo(expectedReason);
	}

	private Collection persistCollection(String title) {
		Collection collection = Collection.create(title, "컬렉션 설명", null, true, 1L);
		entityManager.persist(collection);
		return collection;
	}

	private void persistCollectionContent(Collection collection, Content content, String reason) {
		entityManager.persist(CollectionContent.create(collection, content.getId(), false, reason, 0));
	}
}
