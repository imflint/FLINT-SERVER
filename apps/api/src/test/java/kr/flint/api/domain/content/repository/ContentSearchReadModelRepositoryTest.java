package kr.flint.api.domain.content.repository;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import java.sql.SQLException;
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
import kr.flint.content.domain.Content;
import kr.flint.content.domain.MediaType;
import kr.flint.shared.config.QueryDslConfig;

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
        var repository = new ContentSearchNativeRepository(entityManager, true, true);
        String sql = repository.searchSql(ContentSearchCondition.of("it", null, null, null, 20), null);
        assertThat(sql.indexOf("LIMIT :queryLimit")).isLessThan(sql.indexOf("STRAIGHT_JOIN content c"));
        List<?> plan = entityManager.createNativeQuery("""
            EXPLAIN SELECT FTS_DOC_ID,MATCH(search_title) AGAINST('+it' IN BOOLEAN MODE) score
            FROM content_search_document WHERE MATCH(search_title) AGAINST('+it' IN BOOLEAN MODE)>0
            """).getResultList();
        Object[] row = (Object[]) plan.getFirst();
        assertThat(row[4]).isEqualTo("fulltext");
        assertThat(String.valueOf(row[row.length - 1])).contains("Using index");
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
    }
}
