package kr.flint.api.domain.content.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.transaction.TestTransaction;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.persistence.EntityManager;
import kr.flint.api.domain.content.dto.ContentSearchCondition;
import kr.flint.api.domain.content.dto.ContentSearchCursor;
import kr.flint.content.domain.Content;
import kr.flint.content.domain.MediaType;
import kr.flint.shared.config.QueryDslConfig;
import kr.flint.shared.exception.GeneralException;

@DataJpaTest
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EntityScan(basePackageClasses = Content.class)
@Import(QueryDslConfig.class)
class ContentSearchReadModelRepositoryTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.8")
        .withCommand("--log-bin-trust-function-creators=1");

    @Autowired EntityManager entityManager;
    @Autowired DataSource dataSource;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
    }

    @BeforeEach
    void prepare() throws SQLException {
        ContentSearchReadModelFixture.install(dataSource);
        entityManager.createNativeQuery("DELETE FROM content_genre").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM content").executeUpdate();
        commit();
        TestTransaction.start();
    }

    @Test
    void sourceChangesAndRollbackKeepDocumentAndFullTextConsistent() {
        Content content = Content.createLocalized(101L, MediaType.MOVIE, "해리 포터", "Harry Potter", 2001, null, null, null);
        entityManager.persist(content);
        entityManager.flush();
        long docId = docId(content.getId());
        assertDocument(content);
        commit();
        assertMatches("harry potter", content.getId());

        TestTransaction.start();
        Content managed = entityManager.find(Content.class, content.getId());
        managed.updateLocalizedMetadata("바나나", "Banana", 2002, null, null, null);
        entityManager.flush();
        assertDocument(managed);
        assertThat(docId(content.getId())).isGreaterThan(docId);
        TestTransaction.flagForRollback();
        TestTransaction.end();
        entityManager.clear();
        entityManager.createNativeQuery("ANALYZE TABLE content_search_document").getResultList();
        assertMatches("harry potter", content.getId());
        assertMatches("banana");

        TestTransaction.start();
        managed = entityManager.find(Content.class, content.getId());
        managed.updateLocalizedMetadata("바나나", "Banana", 2002, null, null, null);
        commit();
        assertMatches("harry potter");
        assertMatches("banana", content.getId());
        TestTransaction.start();
        entityManager.remove(entityManager.find(Content.class, content.getId()));
        entityManager.flush();
        assertThat(countDocuments(content.getId())).isZero();
        TestTransaction.flagForRollback();
        TestTransaction.end();
        entityManager.clear();
        assertMatches("banana", content.getId());
        TestTransaction.start();
        entityManager.remove(entityManager.find(Content.class, content.getId()));
        commit();
        assertThat(countDocuments(content.getId())).isZero();
        assertMatches("banana");
    }

    @Test
    void counterChangesDoNotRewriteSearchAndDeletedIdsAreNotReused() {
        Content content = Content.createLocalized(102L, MediaType.MOVIE, null, "It Follows", 2014, null, null, null);
        entityManager.persist(content);
        entityManager.flush();
        long firstDocId = docId(content.getId());
        commit();
        TestTransaction.start();
        entityManager.find(Content.class, content.getId()).increaseBookmarkCount();
        commit();
        assertThat(docId(content.getId())).isEqualTo(firstDocId);
        assertMatches("it follows", content.getId());
        TestTransaction.start();
        entityManager.remove(entityManager.find(Content.class, content.getId()));
        commit();
        TestTransaction.start();
        entityManager.createNativeQuery("""
            INSERT INTO content(id,tmdb_id,media_type,title,title_en,normalized_title_en,search_title,year,bookmark_count)
            VALUES(:id,102,'MOVIE','It Follows','It Follows','itfollows','It Follows itfollows',2014,0)
            """).setParameter("id", content.getId()).executeUpdate();
        commit();
        assertThat(docId(content.getId())).isGreaterThan(firstDocId);
        assertMatches("it follows", content.getId());
    }

    @Test
    void candidateUsesCoveringFullTextAndPageLimitPrecedesContentHydration() {
        entityManager.persist(Content.createLocalized(103L, MediaType.MOVIE, null, "It Follows", 2014, null, null, null));
        commit();
        var repository = new ContentSearchNativeRepository(entityManager, true, true);
        var condition = ContentSearchCondition.of("it", null, null, null, 20);
        String sql = repository.searchSql(condition, null);
        assertThat(sql).doesNotContain("STRAIGHT_JOIN content c");
        assertThat(sql).endsWith("LIMIT :queryLimit");
        assertThat(sql.split("LIMIT", -1)).hasSize(2);
        List<?> plan = entityManager.createNativeQuery("""
            EXPLAIN SELECT FTS_DOC_ID,MATCH(search_title) AGAINST('+it' IN BOOLEAN MODE) score
            FROM content_search_document WHERE MATCH(search_title) AGAINST('+it' IN BOOLEAN MODE)>0
            """).getResultList();
        Object[] row = (Object[]) plan.getFirst();
        assertThat(row[4]).isEqualTo("fulltext");
        assertThat(String.valueOf(row[row.length - 1])).contains("Using index");
        var query = entityManager.createNativeQuery("EXPLAIN " + sql);
        repository.bindParameters(query, condition, null);
        List<?> fullPlan = query.getResultList();
        List<Object[]> documentReads = fullPlan.stream().map(value -> (Object[]) value)
            .filter(value -> "d".equals(value[2])).toList();
        assertThat(documentReads).isNotEmpty().allSatisfy(value -> {
            assertThat(value[6]).isEqualTo("idx_search_document_rank");
            assertThat(String.valueOf(value[value.length - 1])).contains("Using index");
        });
        assertThat(fullPlan.stream().map(value -> (Object[]) value)
            .filter(value -> "fulltext".equals(value[4])).toList())
            .isNotEmpty().allSatisfy(value ->
                assertThat(String.valueOf(value[value.length - 1])).contains("Using index"));
        assertThat(repository.search(condition, null)).hasSize(1)
            .first().satisfies(value -> assertThat(value.getTitle()).isEqualTo("It Follows"));
    }

    @Test
    void v3CursorTraversesExactAndRelevanceResultsWithoutDuplicates() {
        for (int i = 0; i < 6; i++) {
            entityManager.persist(Content.createLocalized(200L + i, MediaType.MOVIE,
                i == 0 ? "그것" : null, i < 2 ? "It" : "It Follows " + i, 2014, null, null, null));
        }
        commit();
        var repository = new ContentSearchNativeRepository(entityManager, true, true);
        var expected = repository.searchAllKeywords("it");
        assertThat(expected).hasSize(6);
        assertThat(expected.subList(0, 2)).allSatisfy(value -> assertThat(value.getExactMatchRank()).isZero());
        var seen = new ArrayList<Long>();
        ContentSearchCursor cursor = null;
        for (int page = 0; page < 4; page++) {
            var rows = repository.search(ContentSearchCondition.of("Ｉ!Ｔ", null, MediaType.MOVIE, cursor, 2), null);
            if (rows.isEmpty()) break;
            var visible = rows.subList(0, Math.min(2, rows.size()));
            visible.forEach(value -> seen.add(value.getId()));
            var last = visible.getLast();
            cursor = ContentSearchCursor.decode(ContentSearchCursor.keyword(last.getExactMatchRank(), last.getRelevanceScore(), last.getId(), true, true).encode());
        }
        assertThat(seen).containsExactlyElementsOf(expected.stream().map(ContentSearchProjection::getId).toList());
        assertThat(seen).doesNotHaveDuplicates();
        assertThatThrownBy(() -> repository.search(
            ContentSearchCondition.of("it", null, null, ContentSearchCursor.keyword(0, 1, 1L, true), 2), null))
            .isInstanceOf(GeneralException.class);
    }

    private void assertDocument(Content content) {
        Object[] row = (Object[]) entityManager.createNativeQuery("""
            SELECT normalized_title_ko,normalized_title_en,search_title,media_type
            FROM content_search_document WHERE content_id=:id
            """).setParameter("id", content.getId()).getSingleResult();
        assertThat(row).containsExactly(content.getNormalizedTitleKo(), content.getNormalizedTitleEn(), content.getSearchTitle(), content.getMediaType().name());
    }

    private long docId(Long id) {
        return ((Number) entityManager.createNativeQuery("SELECT FTS_DOC_ID FROM content_search_document WHERE content_id=:id")
            .setParameter("id", id).getSingleResult()).longValue();
    }

    private long countDocuments(Long id) {
        return ((Number) entityManager.createNativeQuery("SELECT COUNT(*) FROM content_search_document WHERE content_id=:id")
            .setParameter("id", id).getSingleResult()).longValue();
    }

    private void assertMatches(String keyword, Long... ids) {
        assertThat(new ContentSearchNativeRepository(entityManager, true, true).searchAllKeywords(keyword))
            .extracting(ContentSearchProjection::getId).containsExactly(ids);
    }

    private void commit() {
        entityManager.flush();
        TestTransaction.flagForCommit();
        TestTransaction.end();
        entityManager.clear();
        entityManager.createNativeQuery("ANALYZE TABLE content_search_document").getResultList();
    }
}
