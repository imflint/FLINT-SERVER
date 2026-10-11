package kr.flint.api.domain.content.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.transaction.TestTransaction;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import kr.flint.api.domain.content.dto.ContentSearchCondition;
import kr.flint.api.domain.content.dto.ContentSearchCursor;
import kr.flint.api.domain.search.dto.response.BookmarkedContentSearchRes;
import kr.flint.api.domain.search.repository.SearchQueryRepository;
import kr.flint.bookmark.domain.ContentBookmark;
import kr.flint.content.domain.Content;
import kr.flint.content.domain.MediaType;
import kr.flint.content.domain.Genre;
import kr.flint.content.domain.ContentGenre;
import kr.flint.shared.config.QueryDslConfig;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EntityScan(basePackageClasses = {Content.class, ContentBookmark.class})
@Import(QueryDslConfig.class)
class ContentCharacterSearchRepositoryTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.8")
        .withCommand("--log-bin-trust-function-creators=1", "--innodb-ft-min-token-size=1");
    @Autowired EntityManager em;
    @Autowired DataSource dataSource;
    private JdbcTemplate jdbc;
    private ContentSearchNativeRepository search;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
    }

    @BeforeEach
    void prepare() throws Exception {
        ContentSearchReadModelFixture.install(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        em.createNativeQuery("DELETE FROM content_bookmark").executeUpdate();
        em.createNativeQuery("DELETE FROM content_genre").executeUpdate();
        em.createNativeQuery("DELETE FROM content").executeUpdate();
        commit();
        TestTransaction.start();
        search = new ContentSearchNativeRepository(em, true, true, true, true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"봄", "a", "1", "\uD801\uDC28"})
    void matchesEveryTitlePositionDeduplicatesAndKeepsCursorOrder(String character) {
        Content exact = content(1, character, character);
        Content prefix = content(2, character + "날", null);
        Content suffix = content(3, "늦은 " + character, null);
        Content middle = content(4, "앞" + character + "뒤", "x" + character + "x");
        content(5, "무관한 작품", "xyz");
        commit();
        List<Long> expected = new ArrayList<>(List.of(middle.getId(), suffix.getId(), prefix.getId()));
        expected.sort(Comparator.reverseOrder());
        expected.addFirst(exact.getId());
        String equivalent = character.equals("a") ? "Ａ!🔥" : character.equals("1") ? "１!" : Normalizer.normalize(character, Normalizer.Form.NFD);
        for (String input : List.of(character, equivalent)) {
            assertThat(search.searchAllKeywords(input)).extracting(ContentSearchProjection::getId).containsExactlyElementsOf(expected);
            var seen = new ArrayList<Long>();
            ContentSearchCursor cursor = null;
            for (int page = 0; page < 5; page++) {
                var rows = search.search(condition(input, cursor, 1), null);
                if (rows.isEmpty()) break;
                var first = rows.getFirst();
                seen.add(first.getId());
                assertThat(first.getRelevanceScore()).isZero();
                cursor = ContentSearchCursor.keyword(first.getExactMatchRank(), 0, first.getId(), true, true, true, true);
                assertThat(ContentSearchCursor.decode(cursor.encode())).isEqualTo(cursor);
                assertThat(cursor.version()).isEqualTo(5);
            }
            assertThat(seen).doesNotHaveDuplicates().containsExactlyElementsOf(expected);
        }
        assertThat(new ContentSearchNativeRepository(em, true, true, true).searchAllKeywords(character))
            .extracting(ContentSearchProjection::getId).containsExactly(exact.getId());
        assertThatThrownBy(() -> search.search(condition(character,
            ContentSearchCursor.keyword(0, 0, exact.getId(), true, true, true), 2), null)).hasMessageContaining("cursor");
    }

    @Test
    void likeSearchNeedsNoCharacterTableProcedureOrBackfill() throws Exception {
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='content_search_character'",
            Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA=DATABASE() AND ROUTINE_NAME='flint_refresh_content_search_characters'",
            Integer.class)).isZero();
        Content exact = content(401, "봄", null);
        Content partial = content(402, null, "봄날");
        Content unrelated = content(403, "겨울", null);
        em.persist(ContentBookmark.create(71L, partial.getId()));
        em.persist(ContentBookmark.create(72L, unrelated.getId()));
        commit();
        assertThat(search.search(condition("봄", null, 10), null))
            .extracting(ContentSearchProjection::getId).containsExactly(exact.getId(), partial.getId());
        assertThat(search.searchAllKeywords("봄"))
            .extracting(ContentSearchProjection::getId).containsExactly(exact.getId(), partial.getId());
        assertThat(new SearchQueryRepository(new JPAQueryFactory(em), true, true, true)
            .searchBookmarkedContents(71L, "봄", null, 10))
            .extracting(BookmarkedContentSearchRes::contentId).containsExactly(partial.getId());
    }

    @Test
    void likeSearchDoesNotFoldAccentsInMainLegacyOrSavedSearch() {
        Content exact = content(501, null, "A");
        Content partial = content(502, null, "ba");
        Content accented = content(503, null, "á");
        for (Content value : List.of(exact, partial, accented)) {
            em.persist(ContentBookmark.create(81L, value.getId()));
        }
        commit();
        assertThat(search.search(condition("Ａ!", null, 10), null))
            .extracting(ContentSearchProjection::getId).containsExactly(exact.getId(), partial.getId());
        assertThat(search.searchAllKeywords("Ａ!"))
            .extracting(ContentSearchProjection::getId).containsExactly(exact.getId(), partial.getId());
        assertThat(new SearchQueryRepository(new JPAQueryFactory(em), true, true, true)
            .searchBookmarkedContents(81L, "Ａ!", null, 10))
            .extracting(BookmarkedContentSearchRes::contentId).containsExactly(partial.getId(), exact.getId());
    }

    @Test
    void legacyCapsOnlySingleCharacterAndTwoCharacterSqlAndVersionsAreUnchanged() {
        for (int i = 0; i < 55; i++) content(100 + i, "봄날", "Spring Day");
        commit();
        assertThat(search.searchAllKeywords("봄")).hasSize(50);
        assertThat(search.searchAllKeywords("봄날")).hasSize(55);
        var old = new ContentSearchNativeRepository(em, true, true, true);
        for (String keyword : List.of("봄날", "spring day", "it")) {
            var condition = condition(keyword, null, 10);
            assertThat(search.searchSql(condition, null)).isEqualTo(old.searchSql(condition, null));
            assertThat(search.usesSingleCharacterInfix(keyword)).isFalse();
        }
        var previous = ContentSearchCursor.keyword(0, 0, 1L, true, true, true);
        previous.validateKeywordVersion(true, true, true, false);
        assertThatThrownBy(() -> ContentSearchCursor.keyword(2, 0, 1L, true, true, true, true)).hasMessageContaining("cursor");
        assertThatThrownBy(() -> ContentSearchCursor.keyword(1, 0.1, 1L, true, true, true, true)).hasMessageContaining("cursor");
        var v5 = ContentSearchCursor.keyword(1, 0, 1L, true, true, true, true);
        assertThatThrownBy(() -> old.search(condition("봄", v5, 10), null)).hasMessageContaining("cursor");
        assertThatThrownBy(() -> search.search(condition("봄날", v5, 10), null)).hasMessageContaining("cursor");
        assertThatThrownBy(() -> new ContentSearchNativeRepository(em, true, false, false, true))
            .hasMessageContaining("requires localized search");
    }

    @Test
    void savedLikeSearchKeepsOwnLatestBookmarks() {
        Content exact = content(201, "봄", null);
        Content partial = content(202, "늦은 봄날", null);
        Content other = content(203, "다른 작품", null);
        ContentBookmark first = ContentBookmark.create(41L, exact.getId());
        ContentBookmark second = ContentBookmark.create(41L, partial.getId());
        for (ContentBookmark bookmark : List.of(first, second, ContentBookmark.create(41L, other.getId()),
            ContentBookmark.create(42L, partial.getId()))) em.persist(bookmark);
        commit();
        var repository = new SearchQueryRepository(new JPAQueryFactory(em), true, true, true);
        assertThat(repository.searchBookmarkedContents(41L, "봄!", null, 1))
            .extracting(BookmarkedContentSearchRes::contentId).containsExactly(partial.getId(), exact.getId());
        assertThat(repository.searchBookmarkedContents(41L, "봄", second.getId(), 1))
            .extracting(BookmarkedContentSearchRes::contentId).containsExactly(exact.getId());
        assertThat(repository.searchBookmarkedContents(42L, "봄", null, 10))
            .extracting(BookmarkedContentSearchRes::contentId).containsExactly(partial.getId());
        assertThat(repository.searchBookmarkedContents(43L, "봄", null, 10)).isEmpty();
    }

    @Test
    void rangePlansKeepFiltersBeforeLimitAndMeasureCommonRareAndEmptyCases() {
        Genre common = Genre.create("액션");
        Genre sparse = Genre.create("로맨스");
        Genre empty = Genre.create("공포");
        for (Genre genre : List.of(common, sparse, empty)) em.persist(genre);
        for (int i = 0; i < 1000; i++) {
            Content value = Content.createLocalized(10000L + i, i % 10 == 0 ? MediaType.TV : MediaType.MOVIE,
                "앞봄" + i + "뒤" + (i == 0 ? "묘" : ""), "a" + i + "z", 2020, null, null, null);
            em.persist(value);
            if (i % 2 == 0) em.persist(ContentGenre.create(value, common));
            if (i == 0) em.persist(ContentGenre.create(value, sparse));
        }
        commit();
        for (String table : List.of("content_search_document", "content_genre")) {
            jdbc.execute("ANALYZE TABLE " + table);
        }
        record Case(String name, String keyword, Long genreId, MediaType media) {}
        for (Case sample : List.of(new Case("common", "봄", null, null), new Case("english", "a", null, null),
            new Case("rare", "묘", null, null), new Case("frequent-genre", "봄", common.getId(), null),
            new Case("sparse-genre", "봄", sparse.getId(), null), new Case("empty-genre", "봄", empty.getId(), null),
            new Case("media", "봄", null, MediaType.TV))) {
            var condition = ContentSearchCondition.of(sample.keyword(), null, sample.media(), null, 10);
            String sql = search.singleCharacterSql(condition, sample.genreId(), 1);
            assertThat(sql).contains("LIKE :characterPattern").doesNotContain("content_search_character", "MATCH(", "LOCATE(", "relevanceScore DESC");
            var explain = em.createNativeQuery("EXPLAIN " + sql);
            search.bindSingleCharacterParameters(explain, condition, sample.genreId(), 1, 11);
            List<?> plan = explain.getResultList();
            Object[] document = (Object[]) plan.stream().filter(row -> ((Object[]) row)[2].equals("d")).findFirst().orElseThrow();
            assertThat(document[6]).isEqualTo("uk_search_document_content");
            assertThat(plan.stream().map(row -> String.valueOf(((Object[]) row)[11])).toList())
                .allSatisfy(extra -> assertThat(extra).doesNotContain("filesort"));
            long start = System.nanoTime();
            var first = search.search(condition, sample.genreId());
            double firstMs = (System.nanoTime() - start) / 1_000_000.0;
            ContentSearchCursor cursor = first.isEmpty() ? null : ContentSearchCursor.keyword(1, 0,
                first.get(Math.min(9, first.size() - 1)).getId(), true, true, false, true);
            int hops = sample.media() != null ? 7 : ((sample.genreId() == null && !sample.name().equals("rare"))
                || sample.name().equals("frequent-genre") ? 39 : 0);
            for (int page = 0; page < hops && cursor != null; page++) {
                var intermediate = search.search(ContentSearchCondition.of(sample.keyword(), null, sample.media(), cursor, 10), sample.genreId());
                if (intermediate.isEmpty()) break;
                cursor = ContentSearchCursor.keyword(1, 0, intermediate.get(Math.min(9, intermediate.size() - 1)).getId(), true, true, false, true);
            }
            start = System.nanoTime();
            var deepCondition = ContentSearchCondition.of(sample.keyword(), null, sample.media(), cursor, 10);
            var next = search.search(deepCondition, sample.genreId());
            double nextMs = (System.nanoTime() - start) / 1_000_000.0;
            var analysis = em.createNativeQuery("EXPLAIN ANALYZE " + sql);
            search.bindSingleCharacterParameters(analysis, condition, sample.genreId(), 1, 11);
            String actual = analysis.getSingleResult().toString();
            System.out.printf(java.util.Locale.ROOT, "CHAR_SEARCH_FIXTURE case=%s first_ms=%.3f next_ms=%.3f rows=%d/%d plan=%s%n",
                sample.name(), firstMs, nextMs, first.size(), next.size(), actual.replace('\n', '|'));
            var deepAnalysis = em.createNativeQuery("EXPLAIN ANALYZE " + search.singleCharacterSql(deepCondition, sample.genreId(), 1));
            search.bindSingleCharacterParameters(deepAnalysis, deepCondition, sample.genreId(), 1, 11);
            String deepPlan = deepAnalysis.getSingleResult().toString();
            assertThat(deepPlan).contains("uk_search_document_content").doesNotContain("Table scan on d");
            System.out.printf("CHAR_SEARCH_DEEP case=%s page=%d plan=%s%n", sample.name(), hops + 2,
                deepPlan.replace('\n', '|'));
            if (sample.genreId() != null && sample.genreId().equals(empty.getId())) assertThat(first).isEmpty();
            if (sample.genreId() != null && sample.genreId().equals(sparse.getId())) assertThat(first).hasSize(1);
            if (sample.media() != null) {
                assertThat(first).allSatisfy(row -> assertThat(jdbc.queryForObject("SELECT media_type FROM content WHERE id=?", String.class, row.getId())).isEqualTo("TV"));
            }
            if (!first.isEmpty()) {
                assertThat(next.stream().map(ContentSearchProjection::getId).toList())
                    .doesNotContainAnyElementsOf(first.subList(0, Math.min(10, first.size())).stream().map(ContentSearchProjection::getId).toList());
            }
        }
    }

    private Content content(long tmdbId, String ko, String en) {
        Content content = Content.createLocalized(tmdbId, MediaType.MOVIE, ko, en, 2020, null, null, null);
        em.persist(content);
        em.flush();
        return content;
    }

    private ContentSearchCondition condition(String keyword, ContentSearchCursor cursor, int size) {
        return ContentSearchCondition.of(keyword, null, null, cursor, size);
    }

    private void commit() {
        em.flush();
        TestTransaction.flagForCommit();
        TestTransaction.end();
        em.clear();
    }
}
