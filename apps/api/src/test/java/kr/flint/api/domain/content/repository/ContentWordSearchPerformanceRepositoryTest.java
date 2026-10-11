package kr.flint.api.domain.content.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

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
import kr.flint.shared.config.QueryDslConfig;

@DataJpaTest
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EntityScan(basePackageClasses = Content.class)
@Import(QueryDslConfig.class)
class ContentWordSearchPerformanceRepositoryTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.8")
        .withCommand("--log-bin-trust-function-creators=1", "--innodb-ft-min-token-size=1");

    @Autowired EntityManager entityManager;
    @Autowired DataSource dataSource;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
    }

    @Test
    void wordPhraseReducesCandidatesOnTheSameSubstringHeavyFixture() throws SQLException {
        ContentSearchReadModelFixture.install(dataSource);
        String digits = "(SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 "
            + "UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9)";
        String sequence = "(SELECT a.n+10*b.n+100*c.n+1000*d.n n FROM " + digits + " a CROSS JOIN "
            + digits + " b CROSS JOIN " + digits + " c CROSS JOIN " + digits + " d) numbers";
        entityManager.createNativeQuery("""
            INSERT INTO content(id,tmdb_id,media_type,title,title_en,normalized_title_en,search_title,word_title_en,year,bookmark_count)
            SELECT 800000+n,800000+n,'MOVIE',title,title,REPLACE(LOWER(title),' ',''),
                CONCAT(title,' ',REPLACE(LOWER(title),' ','')),LOWER(title),2020,0
            FROM (SELECT n, IF(n<100,CONCAT('It Follows film ',n),
                IF(n<8000,CONCAT('British editor film ',n),CONCAT('Solar moon film ',n))) AS title FROM
            """ + sequence + " WHERE n<9000) fixture").executeUpdate();
        TestTransaction.flagForCommit();
        TestTransaction.end();
        entityManager.clear();
        entityManager.createNativeQuery("ANALYZE TABLE content_search_document").getResultList();
        long oldCandidates = ((Number) entityManager.createNativeQuery("""
            SELECT COUNT(*) FROM content_search_document WHERE MATCH(search_title) AGAINST('+it' IN BOOLEAN MODE)>0
            """).getSingleResult()).longValue();
        long wordCandidates = ((Number) entityManager.createNativeQuery("""
            SELECT COUNT(*) FROM content_search_document WHERE MATCH(word_title_ko,word_title_en) AGAINST('"it"' IN BOOLEAN MODE)>0
            """).getSingleResult()).longValue();
        // A nonmatching cohort prevents all-document bigrams from having zero IDF.
        assertThat(oldCandidates).isEqualTo(8000);
        assertThat(wordCandidates).isEqualTo(100);
        var old = new ContentSearchNativeRepository(entityManager, true, true);
        var words = new ContentSearchNativeRepository(entityManager, true, true, true);
        var first = ContentSearchCondition.of("it", null, null, null, 20);
        String sql = words.searchSql(first, null);
        var explain = entityManager.createNativeQuery("EXPLAIN " + sql);
        words.bindParameters(explain, first, null);
        @SuppressWarnings("unchecked")
        List<Object[]> executionPlan = explain.getResultList();
        assertThat(executionPlan)
            .anySatisfy(row -> assertThat(row[6]).isEqualTo("ft_search_document_words"));
        for (var search : List.of(old, words)) {
            boolean wordMode = search.usesEnglishWords("it");
            List<Double> firstTimes = new ArrayList<>();
            List<Double> deepTimes = new ArrayList<>();
            var deep = first;
            for (int page = 0; page < 3; page++) {
                var pageRows = search.search(deep, null);
                assertThat(pageRows).as("%s page %s", wordMode ? "words" : "bigram", page + 1).hasSize(21);
                var last = pageRows.get(19);
                var cursor = ContentSearchCursor.keyword(last.getExactMatchRank(), last.getRelevanceScore(), last.getId(), true, true, wordMode);
                deep = ContentSearchCondition.of("it", null, null, cursor, 20);
            }
            for (int sample = 0; sample < 5; sample++) {
                long started = System.nanoTime();
                assertThat(search.search(first, null)).hasSize(21);
                firstTimes.add((System.nanoTime() - started) / 1_000_000.0);
                started = System.nanoTime();
                assertThat(search.search(deep, null)).hasSize(21);
                deepTimes.add((System.nanoTime() - started) / 1_000_000.0);
            }
            firstTimes.sort(Double::compare);
            deepTimes.sort(Double::compare);
            System.out.printf(java.util.Locale.ROOT,
                "WORD_SEARCH_FIXTURE mode=%s documents=9000 candidates=%d first_median_ms=%.2f page4_median_ms=%.2f samples=5%n",
                wordMode ? "words" : "bigram", wordMode ? wordCandidates : oldCandidates, firstTimes.get(2), deepTimes.get(2));
        }
    }

}
