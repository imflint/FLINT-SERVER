package kr.flint.api.domain.content.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
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
	void searchContentsMatchesAllGenres() {
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
			contentQueryRepository.searchContents(null, List.of("액션", "로맨스"), null, 1, 10);

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
			contentQueryRepository.searchContents(null, List.of("액션", "액션"), null, 1, 10);

		// then
		assertThat(results)
			.extracting(GetContentSearchRes::title)
			.containsExactly("액션 콘텐츠");
	}

	@Test
	@DisplayName("장르명 공백과 빈 문자열은 정규화해서 검색")
	void genreNamesAreTrimmedAndBlankNamesAreIgnored() {
		// given
		Genre action = persistGenre("액션");
		Content actionContent = persistContent(2101L, "공백 정규화 콘텐츠", 1);
		persistContentGenres(actionContent, action);
		entityManager.flush();
		entityManager.clear();

		// when
		List<GetContentSearchRes> results = contentQueryRepository.searchContents(
			null,
			Arrays.asList(" 액션 ", " ", "", null, "액션"),
			null,
			1,
			10
		);

		// then
		assertThat(results)
			.extracting(GetContentSearchRes::title)
			.containsExactly("공백 정규화 콘텐츠");
	}

	@Test
	@DisplayName("keyword는 콘텐츠 제목 부분 일치로 검색")
	void searchContentsByKeyword() {
		// given
		persistContent(3001L, "눈물의 여왕", 7);
		persistContent(3002L, "반짝이는 워터멜론", 10);
		persistContent(3003L, "눈부신 하루", 3);
		entityManager.flush();
		entityManager.clear();

		// when
		List<GetContentSearchRes> results =
			contentQueryRepository.searchContents("눈", List.of(), null, 1, 10);

		// then
		assertThat(results)
			.extracting(GetContentSearchRes::title)
			.containsExactly("눈물의 여왕", "눈부신 하루");
	}

	@Test
	@DisplayName("mediaType을 지정하면 해당 타입만 검색")
	void searchContentsByMediaType() {
		// given
		persistContent(4001L, "영화 콘텐츠", MediaType.MOVIE, 7);
		persistContent(4002L, "TV 콘텐츠", MediaType.TV, 3);
		entityManager.flush();
		entityManager.clear();

		// when
		List<GetContentSearchRes> results =
			contentQueryRepository.searchContents(null, List.of(), MediaType.TV, 1, 10);

		// then
		assertThat(results)
			.extracting(GetContentSearchRes::title)
			.containsExactly("TV 콘텐츠");
	}

	@Test
	@DisplayName("keyword, genre, mediaType 조건을 모두 AND로 검색")
	void searchContentsWithAllConditions() {
		// given
		Genre action = persistGenre("액션");
		Genre romance = persistGenre("로맨스");

		Content tvMatched = persistContent(5001L, "눈물 액션 로맨스", MediaType.TV, 1);
		Content movieMatchedTitleAndGenres = persistContent(5002L, "눈물 액션 로맨스 영화", MediaType.MOVIE, 10);
		Content tvMatchedGenresOnly = persistContent(5003L, "다른 액션 로맨스", MediaType.TV, 9);
		Content tvMatchedTitleOnly = persistContent(5004L, "눈물 액션", MediaType.TV, 8);

		persistContentGenres(tvMatched, action, romance);
		persistContentGenres(movieMatchedTitleAndGenres, action, romance);
		persistContentGenres(tvMatchedGenresOnly, action, romance);
		persistContentGenres(tvMatchedTitleOnly, action);
		entityManager.flush();
		entityManager.clear();

		// when
		List<GetContentSearchRes> results =
			contentQueryRepository.searchContents("눈물", List.of("액션", "로맨스"), MediaType.TV, 1, 10);

		// then
		assertThat(results)
			.extracting(GetContentSearchRes::title)
			.containsExactly("눈물 액션 로맨스");
	}

	@Test
	@DisplayName("조건이 없으면 전체 콘텐츠를 인기순으로 조회")
	void searchContentsWithoutConditionsOrdersByPopularity() {
		// given
		persistContent(6001L, "북마크 1", 1);
		persistContent(6002L, "북마크 5", 5);
		persistContent(6003L, "북마크 3", 3);
		entityManager.flush();
		entityManager.clear();

		// when
		List<GetContentSearchRes> results =
			contentQueryRepository.searchContents(null, List.of(), null, 1, 10);

		// then
		assertThat(results)
			.extracting(GetContentSearchRes::title)
			.containsExactly("북마크 5", "북마크 3", "북마크 1");
	}

	private Genre persistGenre(String name) {
		Genre genre = Genre.create(name);
		entityManager.persist(genre);
		return genre;
	}

	private Content persistContent(Long tmdbId, String title, int bookmarkCount) {
		return persistContent(tmdbId, title, MediaType.MOVIE, bookmarkCount);
	}

	private Content persistContent(Long tmdbId, String title, MediaType mediaType, int bookmarkCount) {
		Content content = Content.create(
			tmdbId,
			mediaType,
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
