package kr.flint.api.domain.content.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.persistence.EntityManager;
import kr.flint.api.domain.search.dto.response.GetContentSearchRes;
import kr.flint.content.domain.Content;
import kr.flint.content.domain.ContentGenre;
import kr.flint.content.domain.Genre;
import kr.flint.content.domain.MediaType;
import kr.flint.shared.config.QueryDslConfig;

@DataJpaTest
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EntityScan(basePackageClasses = Content.class)
@Import({ContentQueryRepository.class, QueryDslConfig.class})
class ContentQueryRepositoryTest {

	@Container
	static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
		.withDatabaseName("flint_test")
		.withUsername("flint")
		.withPassword("flint");

	private final EntityManager entityManager;
	private final ContentQueryRepository contentQueryRepository;

	@Autowired
	ContentQueryRepositoryTest(
		EntityManager entityManager,
		ContentQueryRepository contentQueryRepository
	) {
		this.entityManager = entityManager;
		this.contentQueryRepository = contentQueryRepository;
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
	@DisplayName("요청한 모든 장르를 가진 콘텐츠만 인기순으로 조회")
	void findPopularByGenreNamesMatchesAllGenres() {
		// given
		Genre action = persistGenre("액션");
		Genre romance = persistGenre("로맨스");
		Genre drama = persistGenre("드라마");

		Content actionOnly = persistContent(1001L, "액션만", 10);
		Content actionRomance = persistContent(1002L, "액션 로맨스", 5);
		Content romanceDrama = persistContent(1003L, "로맨스 드라마", 8);
		Content actionRomanceDrama = persistContent(1004L, "액션 로맨스 드라마", 3);

		persistContentGenres(actionOnly, action);
		persistContentGenres(actionRomance, action, romance);
		persistContentGenres(romanceDrama, romance, drama);
		persistContentGenres(actionRomanceDrama, action, romance, drama);
		entityManager.flush();
		entityManager.clear();

		// when
		List<GetContentSearchRes> results =
			contentQueryRepository.findPopularByGenreNames(List.of("액션", "로맨스"), 1, 10);

		// then
		assertThat(results)
			.extracting(GetContentSearchRes::title)
			.containsExactly("액션 로맨스", "액션 로맨스 드라마");
	}

	@Test
	@DisplayName("중복 장르명은 단일 장르 조건처럼 처리")
	void duplicatedGenreNamesAreDeduplicated() {
		// given
		Genre action = persistGenre("액션");
		Content actionContent = persistContent(2001L, "액션 콘텐츠", 1);
		persistContentGenres(actionContent, action);
		entityManager.flush();
		entityManager.clear();

		// when
		List<GetContentSearchRes> results =
			contentQueryRepository.findPopularByGenreNames(List.of("액션", "액션"), 1, 10);

		// then
		assertThat(results)
			.extracting(GetContentSearchRes::title)
			.containsExactly("액션 콘텐츠");
	}

	private Genre persistGenre(String name) {
		Genre genre = Genre.create(name);
		entityManager.persist(genre);
		return genre;
	}

	private Content persistContent(Long tmdbId, String title, int bookmarkCount) {
		Content content = Content.create(
			tmdbId,
			MediaType.MOVIE,
			title,
			2026,
			"감독",
			"설명",
			"poster.jpg"
		);
		IntStream.range(0, bookmarkCount).forEach(ignored -> content.increaseBookmarkCount());
		entityManager.persist(content);
		return content;
	}

	private void persistContentGenres(Content content, Genre... genres) {
		for (Genre genre : genres) {
			entityManager.persist(ContentGenre.create(content, genre));
		}
	}
}
