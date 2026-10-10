package kr.flint.api.domain.content.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.stream.IntStream;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.context.transaction.TestTransaction;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import kr.flint.api.domain.content.dto.ContentSearchCondition;
import kr.flint.api.domain.content.dto.ContentSearchCursor;
import kr.flint.api.domain.content.dto.GetContentDetailRes;
import kr.flint.api.domain.content.repository.ContentQueryRepository.BookmarkedContentRow;
import kr.flint.api.domain.content.repository.ContentQueryRepository.ContentSearchRow;
import kr.flint.bookmark.domain.ContentBookmark;
import kr.flint.content.domain.Content;
import kr.flint.content.domain.ContentGenre;
import kr.flint.content.domain.Genre;
import kr.flint.content.domain.MediaType;
import kr.flint.ott.domain.OttContent;
import kr.flint.ott.domain.OttProvider;
import kr.flint.shared.config.QueryDslConfig;
import kr.flint.shared.exception.GeneralException;

@DataJpaTest
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EntityScan(basePackageClasses = {Content.class, ContentBookmark.class, OttContent.class, OttProvider.class})
@Import({ContentQueryRepository.class, ContentSearchNativeRepository.class, QueryDslConfig.class,
    kr.flint.api.domain.search.repository.SearchQueryRepository.class})
@Sql(
	statements = {
		"DELETE FROM content_bookmark",
		"DELETE FROM ott_content",
		"DELETE FROM ott_provider",
		"DELETE FROM content_genre",
		"DELETE FROM genre",
		"DELETE FROM content"
	},
	config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED),
	executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class ContentQueryRepositoryTest {

	@Container
	static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.8")
		.withDatabaseName("flint_test")
		.withUsername("flint")
		.withPassword("flint");

	private final EntityManager entityManager;
	private final ContentQueryRepository contentQueryRepository;
	private final DataSource dataSource;
	@Autowired
	private kr.flint.api.domain.search.repository.SearchQueryRepository searchQueryRepository;

	@Autowired
	ContentQueryRepositoryTest(
		EntityManager entityManager,
		ContentQueryRepository contentQueryRepository,
		DataSource dataSource
	) {
		this.entityManager = entityManager;
		this.contentQueryRepository = contentQueryRepository;
		this.dataSource = dataSource;
	}

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
		registry.add("spring.datasource.username", MYSQL::getUsername);
		registry.add("spring.datasource.password", MYSQL::getPassword);
		registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
		registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MySQLDialect");
		registry.add("flint.content.localized-search-enabled", () -> "true");
	}

	@BeforeEach
	void ensureFullTextIndex() throws SQLException {
		for (String ddl : List.of(
			"CREATE FULLTEXT INDEX ft_content_search_title_ngram ON content (search_title) WITH PARSER ngram",
			"CREATE FULLTEXT INDEX ft_content_title_ngram ON content (title) WITH PARSER ngram",
			"CREATE INDEX idx_content_popular ON content (bookmark_count DESC, id DESC)",
			"CREATE INDEX idx_content_media_popular ON content (media_type, bookmark_count DESC, id DESC)",
			"CREATE INDEX idx_content_title_lower ON content ((LOWER(title))) ALGORITHM=INPLACE LOCK=SHARED",
			"CREATE INDEX idx_content_normalized_title_ko ON content (normalized_title_ko) ALGORITHM=INPLACE LOCK=NONE",
			"CREATE INDEX idx_content_normalized_title_en ON content (normalized_title_en) ALGORITHM=INPLACE LOCK=NONE"
		)) {
			try (Connection connection = dataSource.getConnection();
				 Statement statement = connection.createStatement()) {
				statement.execute("CREATE TABLE IF NOT EXISTS content_search_stopword(value VARCHAR(30)) ENGINE=InnoDB");
				statement.execute("SET SESSION innodb_ft_user_stopword_table='flint_test/content_search_stopword'");
				statement.execute(ddl);
			} catch (SQLException exception) {
				if (exception.getErrorCode() != 1061) {
					throw exception;
				}
			}
		}
	}

	@Test
	void threeSearchPathsShareNormalizedMatchingAndBookmarkOwnership() {
		Content exact = Content.createLocalized(90001L, MediaType.MOVIE, "해리 포터", "Harry Potter", 2001, null, null, "poster");
		Content unrelated = Content.create(90002L, MediaType.MOVIE, "다른 작품", 2020, null, null, "poster");
		entityManager.persist(exact);
		entityManager.persist(unrelated);
		entityManager.persist(ContentBookmark.create(1L, exact.getId()));
		entityManager.persist(ContentBookmark.create(2L, unrelated.getId()));
		commitFullTextFixtures();
		for (String keyword : List.of("해 리 포 터!", "ＨＡＲＲＹ ＰＯＴＴＥＲ", "harry potter")) {
			assertThat(repository(true).searchContents(condition(keyword, null, null, 10)))
				.extracting(ContentSearchRow::id).containsExactly(exact.getId());
			assertThat(new ContentSearchNativeRepository(entityManager, true).searchAllKeywords(keyword))
				.extracting(ContentSearchProjection::getId).containsExactly(exact.getId());
			assertThat(searchQueryRepository.searchBookmarkedContents(1L, keyword, null, 10))
				.extracting(kr.flint.api.domain.search.dto.response.BookmarkedContentSearchRes::contentId)
				.containsExactly(exact.getId());
			assertThat(searchQueryRepository.searchBookmarkedContents(2L, keyword, null, 10)).isEmpty();
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"눈🔥", "a!", "!!", "🔥🔥", "Ａ!"})
	void normalizedShortKeywordsAreRejected(String keyword) {
		assertThatThrownBy(() -> repository(true).searchContents(condition(keyword, null, null, 10)))
			.isInstanceOf(GeneralException.class);
		assertThatThrownBy(() -> searchQueryRepository.searchBookmarkedContents(1L, keyword, null, 10))
			.isInstanceOf(GeneralException.class);
	}

	@Test
	void emptyTitleStopwordsKeepPartialEnglishTitleMatches() {
		Content movie = Content.createLocalized(90003L, MediaType.MOVIE, "팔로우", "It Follows", 2014, null, null, "poster");
		entityManager.persist(movie);
		commitFullTextFixtures();
		assertThat(repository(true).searchContents(condition("it", null, null, 10)))
			.extracting(ContentSearchRow::id).containsExactly(movie.getId());
		assertThatThrownBy(() -> repository(true).searchContents(condition("it", null, null,
			ContentSearchCursor.keyword(0, 1.0, movie.getId()), 10))).isInstanceOf(GeneralException.class);
	}

	@Test
	@DisplayName("단일 장르를 포함한 콘텐츠만 중복 없이 인기순으로 조회")
	void searchContentsMatchesSingleGenre() {
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
		List<ContentSearchRow> results =
			contentQueryRepository.searchContents(condition(null, "액션", null, 10));

		// then
		assertThat(results)
			.extracting(ContentSearchRow::title)
			.containsExactly("액션만", "액션 로맨스", "액션 로맨스 드라마");
	}

	@Test
	@DisplayName("DB에 없는 장르를 요청하면 빈 결과를 반환")
	void searchContentsWithMissingGenreReturnsEmpty() {
		// given
		Genre action = persistGenre("액션");
		Content actionContent = persistContent(2001L, "액션 콘텐츠", 1);
		persistContentGenres(actionContent, action);
		entityManager.flush();
		entityManager.clear();

		// when
		List<ContentSearchRow> results =
			contentQueryRepository.searchContents(condition(null, "로맨스", null, 10));

		// then
		assertThat(results).isEmpty();
	}

	@Test
	@DisplayName("정규화된 장르명으로 검색")
	void searchContentsWithNormalizedGenreName() {
		// given
		Genre action = persistGenre("액션");
		Content actionContent = persistContent(2101L, "공백 정규화 콘텐츠", 1);
		persistContentGenres(actionContent, action);
		entityManager.flush();
		entityManager.clear();

		// when
		List<ContentSearchRow> results = contentQueryRepository.searchContents(
			condition(null, " 액션 ", null, 10)
		);

		// then
		assertThat(results)
			.extracting(ContentSearchRow::title)
			.containsExactly("공백 정규화 콘텐츠");
	}

	@Test
	@DisplayName("keyword는 콘텐츠 제목 FULLTEXT로 검색")
	void searchContentsByKeyword() {
		// given
		persistContent(3001L, "눈물의 여왕", 7);
		persistContent(3002L, "반짝이는 워터멜론", 10);
		persistContent(3003L, "눈부신 하루", 3);
		commitFullTextFixtures();

		// when
		List<ContentSearchRow> results =
			contentQueryRepository.searchContents(condition("눈물", null, null, 10));

		// then
		assertThat(results)
			.extracting(ContentSearchRow::title)
			.containsExactly("눈물의 여왕");
	}

	@Test
	@DisplayName("1자 keyword는 부분 검색하지 않고 거절")
	void searchContentsByOneCharacterKeyword() {
		// given
		persistContent(3101L, "눈물의 여왕", 7);
		persistContent(3102L, "반짝이는 워터멜론", 10);
		persistContent(3103L, "눈부신 하루", 3);
		entityManager.flush();
		entityManager.clear();

		// when
		assertThatThrownBy(() -> contentQueryRepository.searchContents(condition("눈", null, null, 10)))
			.isInstanceOf(GeneralException.class)
			.hasMessageContaining("keyword는 2자 이상이어야 합니다.");
	}

	@Test
	@DisplayName("keyword 검색은 인기보다 정규화 완전 일치와 관련도를 우선")
	void keywordSearchOrdersExactMatchBeforePopularity() {
		persistContent(3201L, "해리포터와 불의 잔", 100);
		persistContent(3202L, "해리 포터", 0);
		commitFullTextFixtures();

		List<ContentSearchRow> results =
			contentQueryRepository.searchContents(condition("해리포터", null, null, 10));

		assertThat(results)
			.extracting(ContentSearchRow::title)
			.startsWith("해리 포터");
		assertThat(results.getFirst().exactMatchRank()).isZero();
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
		List<ContentSearchRow> results =
			contentQueryRepository.searchContents(condition(null, null, MediaType.TV, 10));

		// then
		assertThat(results)
			.extracting(ContentSearchRow::title)
			.containsExactly("TV 콘텐츠");
	}

	@Test
	@DisplayName("keyword, mediaType, 단일 장르를 AND로 검색")
	void searchContentsWithAllConditions() {
		// given
		Genre action = persistGenre("액션");
		Genre romance = persistGenre("로맨스");

		Content tvMatched = persistContent(5001L, "눈물 액션 로맨스", MediaType.TV, 1);
		Content movieMatchedTitleAndGenres = persistContent(5002L, "눈물 액션 로맨스 영화", MediaType.MOVIE, 10);
		Content tvMatchedGenresOnly = persistContent(5003L, "다른 액션 로맨스", MediaType.TV, 9);
		Content tvMatchedTitleOnly = persistContent(5004L, "눈물 액션", MediaType.TV, 8);
		Content tvMatchedOtherGenre = persistContent(5005L, "눈물 로맨스", MediaType.TV, 20);

		persistContentGenres(tvMatched, action, romance);
		persistContentGenres(movieMatchedTitleAndGenres, action, romance);
		persistContentGenres(tvMatchedGenresOnly, action, romance);
		persistContentGenres(tvMatchedTitleOnly, action);
		persistContentGenres(tvMatchedOtherGenre, romance);
		commitFullTextFixtures();

		// when
		List<ContentSearchRow> results =
			contentQueryRepository.searchContents(condition("눈물", "액션", MediaType.TV, 10));

		// then
		assertThat(results)
			.extracting(ContentSearchRow::title)
			.containsExactlyInAnyOrder("눈물 액션 로맨스", "눈물 액션");
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
		List<ContentSearchRow> results =
			contentQueryRepository.searchContents(condition(null, null, null, 10));

		// then
		assertThat(results)
			.extracting(ContentSearchRow::title)
			.containsExactly("북마크 5", "북마크 3", "북마크 1");
	}

	@Test
	@DisplayName("cursor token 이후 콘텐츠를 인기순으로 조회")
	void searchContentsWithCursorToken() {
		// given
		Content first = persistContent(7001L, "북마크 10", 10);
		persistContent(7002L, "북마크 7", 7);
		persistContent(7003L, "북마크 5", 5);
		entityManager.flush();
		entityManager.clear();

		// when
		List<ContentSearchRow> results =
			contentQueryRepository.searchContents(
				condition(null, null, null, ContentSearchCursor.of(first.getBookmarkCount(), first.getId()), 1)
			);

		// then
		assertThat(results)
			.extracting(ContentSearchRow::title)
			.containsExactly("북마크 7", "북마크 5");
	}

	@Test
	@DisplayName("동일 bookmarkCount에서는 cursor id보다 작은 콘텐츠를 조회")
	void searchContentsWithCursorTokenTieBreaker() {
		// given
		persistContent(7101L, "동점 첫번째", 3);
		persistContent(7102L, "동점 두번째", 3);
		persistContent(7103L, "낮은 북마크", 2);
		entityManager.flush();
		entityManager.clear();

		List<ContentSearchRow> firstPage = contentQueryRepository.searchContents(condition(null, null, null, 1));
		ContentSearchCursor cursor = ContentSearchCursor.of(firstPage.getFirst().bookmarkCount(), firstPage.getFirst().id());

		// when
		List<ContentSearchRow> results = contentQueryRepository.searchContents(
			condition(null, null, null, cursor, 1)
		);

		// then
		assertThat(results)
			.extracting(ContentSearchRow::title)
			.containsExactly("동점 첫번째".equals(firstPage.getFirst().title()) ? "동점 두번째" : "동점 첫번째", "낮은 북마크");
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	@DisplayName("정확 일치와 FULLTEXT 후보를 중복 제거하고 기존 OR 검색 순서를 유지")
	void keywordUnionPreservesResultsAndScores(boolean localized) {
		persistContent(7201L, "해리포터", 1);
		persistContent(7202L, "해리포터와 불의 잔", 100);
		persistContent(7203L, "해리 포터", 0);
		persistContent(7204L, "관계없는 작품", 10);
		commitFullTextFixtures();

		List<ContentSearchRow> results = repository(localized)
			.searchContents(condition("해리포터", null, null, 10));
		String exact = localized
			? "normalized_title_ko='해리포터' OR normalized_title_en='해리포터'"
			: "LOWER(title)='해리포터'";
		String title = localized ? "search_title" : "title";
		List<?> original = entityManager.createNativeQuery("SELECT id, CASE WHEN " + exact +
			" THEN 0 ELSE 1 END AS exactRank, MATCH(" + title + ") AGAINST ('해리포터' IN NATURAL LANGUAGE MODE) AS score " +
			"FROM content WHERE (" + exact + ") OR MATCH(" + title + ") AGAINST ('해리포터' IN BOOLEAN MODE)>0 " +
			"ORDER BY exactRank, score DESC, id DESC LIMIT 11", Tuple.class).getResultList();

		assertThat(results).extracting(ContentSearchRow::id).doesNotHaveDuplicates()
			.containsExactlyElementsOf(original.stream().map(row -> ((Number) ((Tuple) row).get("id")).longValue()).toList());
		assertThat(results).anyMatch(row -> row.relevanceScore() > 0);
		for (int i = 0; i < results.size(); i++) {
			Tuple row = (Tuple) original.get(i);
			assertThat(results.get(i).exactMatchRank()).isEqualTo(((Number) row.get("exactRank")).intValue());
			assertThat(results.get(i).relevanceScore()).isEqualTo(((Number) row.get("score")).doubleValue());
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	@DisplayName("ngram이 찾지 못하는 정확 일치 결과도 유지")
	void exactMatchSurvivesMissingFullTextToken(boolean localized) {
		Content exact = persistContent(7301L, "A B", 1);
		// Simulate an indexed document without bigrams while exact-match fields remain populated.
		org.springframework.test.util.ReflectionTestUtils.setField(exact, "searchTitle", "A B");
		commitFullTextFixtures();

		List<ContentSearchRow> results = repository(localized)
			.searchContents(condition("A B", null, null, 10));

		assertThat(results).extracting(ContentSearchRow::id).containsExactly(exact.getId());
		assertThat(results.getFirst().exactMatchRank()).isZero();
		assertThat(results.getFirst().relevanceScore()).isZero();
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	@DisplayName("키워드 커서는 완전 일치에서 관련도 결과로 넘어가며 동점과 마지막 페이지 유지")
	void keywordCursorTraversesAllCandidates(boolean localized) {
		persistContent(7401L, "해리포터", 1);
		persistContent(7402L, "해리포터", 2);
		persistContent(7403L, "해리포터 속편", 100);
		persistContent(7404L, "해리포터 속편", 200);
		persistContent(7405L, "해리포터 해리포터 속편", 0);
		persistContent(7406L, "전혀 다른 작품", 300);
		commitFullTextFixtures();
		ContentQueryRepository repository = repository(localized);
		List<ContentSearchRow> all = repository.searchContents(condition("해리포터", null, null, 10));
		List<Long> pageIds = new java.util.ArrayList<>();
		ContentSearchCursor cursor = null;
		for (int i = 0; i < all.size(); i++) {
			List<ContentSearchRow> page = repository.searchContents(condition("해리포터", null, null, cursor, 1));
			assertThat(page).hasSize(Math.min(2, all.size() - i));
			ContentSearchRow first = page.getFirst();
			pageIds.add(first.id());
			cursor = ContentSearchCursor.keyword(first.exactMatchRank(), first.relevanceScore(), first.id(), localized);
		}
		assertThat(pageIds).doesNotHaveDuplicates().containsExactlyElementsOf(all.stream().map(ContentSearchRow::id).toList());
		assertThat(repository.searchContents(condition("해리포터", null, null, cursor, 1))).isEmpty();
	}

	@Test
	@DisplayName("장르 인기순 조회는 미디어 필터를 먼저 적용하고 동점 커서를 유지")
	void genrePopularityPreservesMediaAndCursor() {
		Genre drama = persistGenre("드라마");
		Content movie = persistContent(7501L, "영화", MediaType.MOVIE, 100);
		Content first = persistContent(7502L, "드라마 하나", MediaType.TV, 3);
		Content second = persistContent(7503L, "드라마 둘", MediaType.TV, 3);
		persistContent(7504L, "장르 없는 인기 TV", MediaType.TV, 200);
		persistContentGenres(movie, drama);
		persistContentGenres(first, drama);
		persistContentGenres(second, drama);
		entityManager.flush();
		entityManager.clear();

		List<ContentSearchRow> all = contentQueryRepository.searchContents(condition(null, "드라마", MediaType.TV, 10));
		assertThat(all).hasSize(2);
		ContentSearchRow top = all.getFirst();
		List<ContentSearchRow> next = contentQueryRepository.searchContents(condition(null, "드라마", MediaType.TV,
			ContentSearchCursor.popular(top.bookmarkCount(), top.id()), 1));
		assertThat(next).extracting(ContentSearchRow::id).containsExactly(all.get(1).id());
		assertThat(contentQueryRepository.searchContents(condition(null, "드라마", MediaType.TV,
			ContentSearchCursor.popular(next.getFirst().bookmarkCount(), next.getFirst().id()), 1))).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	@DisplayName("키워드 후보에 장르와 미디어 필터를 적용한 뒤 페이지를 제한")
	void keywordCandidatesAreNotLimitedBeforeFiltering(boolean localized) {
		Genre action = persistGenre("액션");
		for (int i = 0; i < 5; i++) {
			persistContent(7600L + i, "해리포터", MediaType.MOVIE, 10);
		}
		Content matched = persistContent(7606L, "해리포터 속편", MediaType.TV, 0);
		persistContentGenres(matched, action);
		commitFullTextFixtures();

		assertThat(repository(localized).searchContents(condition("해리포터", "액션", MediaType.TV, 1)))
			.extracting(ContentSearchRow::id).containsExactly(matched.getId());
		assertThat(repository(localized).searchContents(condition("없는검색어", "액션", MediaType.TV, 1))).isEmpty();
	}

	@Test
	@DisplayName("빈 장르는 빈 결과이며 기호만 입력한 검색어는 거절")
	void emptyGenreAndSymbolKeywordReturnEmpty() {
		persistGenre("드라마");
		persistContent(7701L, "인기 작품", 100);
		entityManager.flush();
		entityManager.clear();
		assertThat(contentQueryRepository.searchContents(condition(null, "드라마", null, 10))).isEmpty();
		assertThatThrownBy(() -> contentQueryRepository.searchContents(condition("!!", null, null, 10)))
			.isInstanceOf(GeneralException.class);
	}

	private ContentQueryRepository repository(boolean localized) {
		return new ContentQueryRepository(new JPAQueryFactory(entityManager),
			new ContentSearchNativeRepository(entityManager, localized));
	}

	@ParameterizedTest
	@ValueSource(strings = {"banana", "harrypotter", "aaaa"})
	void requiredBigramsPreserveNaturalScoresAndKeywordCursor(String keyword) {
		for (int i = 0; i < 3; i++) {
			Content candidate = Content.createLocalized(91001L + i, MediaType.MOVIE, "영문 작품 " + i,
				keyword.repeat(i + 1) + (i == 0 ? "" : " sequel"), 2020, null, null, "poster");
			entityManager.persist(candidate);
		}
		Content split = Content.createLocalized(91004L, MediaType.MOVIE, "일치하지 않는 작품",
			"ba an na ha ar rr ry yp po ot tt te er aa", 2020, null, null, "poster");
		entityManager.persist(split);
		commitFullTextFixtures();
		List<ContentSearchRow> all = repository(true).searchContents(condition(keyword, null, null, 10));
		assertThat(all).hasSize(3).extracting(ContentSearchRow::id).doesNotContain(split.getId());
		List<?> expected = entityManager.createNativeQuery("""
			SELECT id, CASE WHEN normalized_title_en=:keyword THEN 0 ELSE 1 END exactRank,
			MATCH(search_title) AGAINST (:keyword IN NATURAL LANGUAGE MODE) score FROM content
			WHERE LOCATE(:keyword,normalized_title_en)>0 ORDER BY exactRank,score DESC,id DESC
			""", Tuple.class).setParameter("keyword", keyword).getResultList();
		for (int i = 0; i < all.size(); i++) {
			Tuple original = (Tuple) expected.get(i);
			assertThat(all.get(i).id()).isEqualTo(((Number) original.get("id")).longValue());
			assertThat(all.get(i).relevanceScore()).isEqualTo(((Number) original.get("score")).doubleValue());
		}
		ContentSearchCursor cursor = null;
		for (ContentSearchRow expectedRow : all) {
			List<ContentSearchRow> page = repository(true).searchContents(condition(keyword, null, null, cursor, 1));
			assertThat(page.getFirst().id()).isEqualTo(expectedRow.id());
			cursor = ContentSearchCursor.keyword(expectedRow.exactMatchRank(), expectedRow.relevanceScore(), expectedRow.id(), true);
		}
		assertThat(repository(true).searchContents(condition(keyword, null, null, cursor, 1))).isEmpty();
		assertThat(new ContentSearchNativeRepository(entityManager, true).searchAllKeywords(keyword))
			.extracting(ContentSearchProjection::getId).containsExactlyElementsOf(all.stream().map(ContentSearchRow::id).toList());
		TestTransaction.start();
		entityManager.persist(ContentBookmark.create(1L, all.getFirst().id()));
		entityManager.persist(ContentBookmark.create(1L, split.getId()));
		entityManager.flush();
		assertThat(searchQueryRepository.searchBookmarkedContents(1L, keyword, null, 10))
			.extracting(kr.flint.api.domain.search.dto.response.BookmarkedContentSearchRes::contentId)
			.containsExactly(all.getFirst().id());
	}
	@Test
	@DisplayName("다국어 검색은 영문 정확 일치를 포함하고 양쪽 제목의 일치 후보를 중복 제거")
	void localizedKeywordMatchesEnglishAndDeduplicatesBothTitles() {
		Content bilingual = Content.createLocalized(7801L, MediaType.MOVIE, "해리포터", "Harry Potter",
			2026, "감독", "설명", "poster.jpg");
		Content bothTitles = Content.createLocalized(7802L, MediaType.MOVIE, "Harry Potter", "Harry Potter",
			2026, "감독", "설명", "poster.jpg");
		entityManager.persist(bilingual);
		entityManager.persist(bothTitles);
		persistContent(7803L, "관계없는 작품", 0);
		commitFullTextFixtures();

		List<ContentSearchRow> localized = repository(true)
			.searchContents(condition("Harry Potter", null, null, 10));
		assertThat(localized).extracting(ContentSearchRow::id).doesNotHaveDuplicates()
			.containsExactlyInAnyOrder(bilingual.getId(), bothTitles.getId());
		assertThat(localized).allMatch(row -> row.exactMatchRank() == 0);
		assertThat(repository(false).searchContents(condition("Harry Potter", null, null, 10)))
			.extracting(ContentSearchRow::id).containsExactly(bothTitles.getId());
	}

	@Test
	@DisplayName("북마크 콘텐츠 목록은 구독 여부와 관계없이 작품 제공 OTT를 포함")
	void bookmarkedContentRowsIncludeContentOttProviders() {
		// given
		Long userId = 1L;
		Content content = persistContent(8001L, "OTT 포함 콘텐츠", 1);
		entityManager.flush();

		entityManager.persist(ContentBookmark.create(userId, content.getId()));
		persistOttProvider(9001L, "Netflix", "netflix.svg", 20, true);
		persistOttProvider(9002L, "Wavve", "wavve.svg", 10, true);
		persistOttProvider(9003L, "Inactive", "inactive.svg", 1, false);
		persistOttContent(9001L, content.getId());
		persistOttContent(9002L, content.getId());
		persistOttContent(9003L, content.getId());
		entityManager.flush();
		entityManager.clear();

		// when
		List<BookmarkedContentRow> rows = contentQueryRepository.getBookmarkedContentRows(userId, null, 10);

		// then
		assertThat(rows).hasSize(1);
		assertThat(rows.getFirst().ottSimpleList())
			.extracting(GetContentDetailRes.GetOttSimpleRes::ottName)
			.containsExactly("Wavve", "Netflix");
	}

	@Test
	@DisplayName("북마크 콘텐츠 cursor 페이지는 현재 사용자 관계만 최신순으로 중복 없이 반환")
	void bookmarkedContentRowsAreScopedAndCursorPaginated() {
		Long userId = 1L;
		Content first = persistContent(8101L, "첫 번째", 1);
		Content second = persistContent(8102L, "두 번째", 1);
		Content third = persistContent(8103L, "세 번째", 1);
		Content otherUser = persistContent(8104L, "타 사용자 작품", 1);
		persistContent(8105L, "미저장 작품", 1);
		entityManager.flush();
		persistBookmark(300L, userId, first.getId());
		persistBookmark(200L, userId, second.getId());
		persistBookmark(100L, userId, third.getId());
		persistBookmark(400L, 2L, otherUser.getId());
		entityManager.flush();
		entityManager.clear();

		List<BookmarkedContentRow> firstPage = contentQueryRepository.getBookmarkedContentRows(userId, null, 2);
		List<BookmarkedContentRow> secondPage = contentQueryRepository.getBookmarkedContentRows(userId, 200L, 2);

		assertThat(firstPage).extracting(BookmarkedContentRow::title).containsExactly("첫 번째", "두 번째");
		assertThat(secondPage).extracting(BookmarkedContentRow::title).containsExactly("세 번째");
		assertThat(java.util.stream.Stream.concat(firstPage.stream(), secondPage.stream()))
			.extracting(BookmarkedContentRow::contentId)
			.doesNotHaveDuplicates();
	}

	@Test
	@DisplayName("저장 작품 감독이 Unknown이면 null, 실제 이름이면 그대로 반환")
	void bookmarkedContentRowsNormalizeAuthor() {
		Content unknown = persistContent(8201L, "감독 없음", MediaType.MOVIE, 0, "Unknown");
		Content director = persistContent(8202L, "감독 있음", MediaType.MOVIE, 0, "감독 이름");
		entityManager.flush();
		persistBookmark(200L, 1L, unknown.getId());
		persistBookmark(100L, 1L, director.getId());
		entityManager.flush();
		entityManager.clear();

		List<BookmarkedContentRow> rows = contentQueryRepository.getBookmarkedContentRows(1L, null, 10);

		assertThat(rows).extracting(BookmarkedContentRow::author).containsExactly(null, "감독 이름");
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
		return persistContent(tmdbId, title, mediaType, bookmarkCount, "감독");
	}

	private Content persistContent(
		Long tmdbId,
		String title,
		MediaType mediaType,
		int bookmarkCount,
		String author
	) {
		Content content = Content.create(
			tmdbId,
			mediaType,
			title,
			2026,
			author,
			"설명",
			"poster.jpg"
		);
		IntStream.range(0, bookmarkCount).forEach(ignored -> content.increaseBookmarkCount());
		entityManager.persist(content);
		return content;
	}

	private void persistBookmark(Long id, Long userId, Long contentId) {
		entityManager.createNativeQuery("""
			INSERT INTO content_bookmark (id, user_id, content_id)
			VALUES (:id, :userId, :contentId)
			""")
			.setParameter("id", id)
			.setParameter("userId", userId)
			.setParameter("contentId", contentId)
			.executeUpdate();
	}

	private void persistContentGenres(Content content, Genre... genres) {
		for (Genre genre : genres) {
			entityManager.persist(ContentGenre.create(content, genre));
		}
	}

	private void persistOttProvider(Long id, String name, String logoUrl) {
		persistOttProvider(id, name, logoUrl, 9999, true);
	}

	private void persistOttProvider(Long id, String name, String logoUrl, int displayPriority, boolean active) {
		entityManager.createNativeQuery("""
				INSERT INTO ott_provider (
					id, name, logo_url, url, tmdb_provider_id, display_priority, active
				)
				VALUES (:id, :name, :logoUrl, :url, NULL, :displayPriority, :active)
			""")
			.setParameter("id", id)
			.setParameter("name", name)
			.setParameter("logoUrl", logoUrl)
			.setParameter("url", "https://example.com")
			.setParameter("displayPriority", displayPriority)
			.setParameter("active", active)
			.executeUpdate();
	}

	private void persistOttContent(Long providerId, Long contentId) {
		entityManager.createNativeQuery("""
				INSERT INTO ott_content (id, ott_provider_id, content_id, content_url)
				VALUES (:id, :providerId, :contentId, :contentUrl)
			""")
			.setParameter("id", providerId + contentId)
			.setParameter("providerId", providerId)
			.setParameter("contentId", contentId)
			.setParameter("contentUrl", "https://example.com/watch")
			.executeUpdate();
	}

	private void commitFullTextFixtures() {
		entityManager.flush();
		entityManager.clear();
		TestTransaction.flagForCommit();
		TestTransaction.end();
		// Stabilize FULLTEXT document statistics after replacing the small fixture.
		entityManager.createNativeQuery("ANALYZE TABLE content").getResultList();
	}

	private ContentSearchCondition condition(
		String keyword,
		String genreName,
		MediaType mediaType,
		int size
	) {
		return ContentSearchCondition.of(keyword, genreName, mediaType, null, size);
	}

	private ContentSearchCondition condition(
		String keyword,
		String genreName,
		MediaType mediaType,
		ContentSearchCursor cursor,
		int size
	) {
		return ContentSearchCondition.of(keyword, genreName, mediaType, cursor, size);
	}
}
