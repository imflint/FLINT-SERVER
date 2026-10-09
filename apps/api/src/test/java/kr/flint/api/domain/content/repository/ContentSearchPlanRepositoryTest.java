package kr.flint.api.domain.content.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javax.sql.DataSource;

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
import kr.flint.content.domain.Genre;
import kr.flint.content.domain.GenreCode;
import kr.flint.content.domain.MediaType;
import kr.flint.shared.config.QueryDslConfig;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EntityScan(basePackageClasses = Content.class)
@Import({ContentQueryRepository.class, ContentSearchNativeRepository.class, QueryDslConfig.class})
class ContentSearchPlanRepositoryTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.8")
        .withDatabaseName("flint_plan_test").withUsername("test").withPassword("test");
    @Autowired private EntityManager entityManager;
    @Autowired private ContentQueryRepository contentQueryRepository;
    @Autowired private DataSource dataSource;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        registry.add("flint.content.localized-search-enabled", () -> "true");
    }

    @BeforeEach
    void indexes() throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE content_search_stopword(value VARCHAR(30)) ENGINE=InnoDB");
            statement.execute("SET SESSION innodb_ft_user_stopword_table='flint_plan_test/content_search_stopword'");
            for (String sql : List.of(
                "CREATE FULLTEXT INDEX ft_content_search_title_ngram ON content(search_title) WITH PARSER ngram",
                "CREATE INDEX idx_content_normalized_title_ko ON content(normalized_title_ko)",
                "CREATE INDEX idx_content_normalized_title_en ON content(normalized_title_en)")) {
                statement.execute(sql);
            }
        }
    }

    @Test
    void representativeQueryPlansPreserveOrderedAndCandidateFirstAccess() {
        Genre drama = persistGenre("드라마");
        Genre rare = persistGenre("전쟁");
        Genre empty = persistGenre("공포");
        entityManager.flush();
        String digits = "(SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 "
            + "UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9)";
        String sequence = "(SELECT a.n+10*b.n+100*c.n+1000*d.n+10000*e.n n FROM "
            + digits + " a CROSS JOIN " + digits + " b CROSS JOIN " + digits + " c CROSS JOIN "
            + digits + " d CROSS JOIN " + digits + " e) seq";
        entityManager.createNativeQuery("""
            INSERT INTO content(id,tmdb_id,media_type,title,title_ko,normalized_title_ko,search_title,
            `year`,poster,bookmark_count,created_at,updated_at)
            SELECT 1000000+n,1000000+n,IF(MOD(n,4)<2,'MOVIE','TV'),
            IF(n<30,'해리포터모험',CONCAT('일반작품',n)),IF(n<30,'해리포터모험',CONCAT('일반작품',n)),
            IF(n<30,'해리포터모험',CONCAT('일반작품',n)),IF(n<30,'해리포터모험',CONCAT('일반작품',n)),
            2020,'poster',50000-n,UTC_TIMESTAMP(),UTC_TIMESTAMP() FROM
            """ + sequence + " WHERE n<50000").executeUpdate();
        entityManager.createNativeQuery("""
            INSERT INTO content_genre(id,content_id,genre_id)
            SELECT 2000000+id,id,:genre FROM content WHERE MOD(id,2)=0
            """).setParameter("genre", drama.getId()).executeUpdate();
        entityManager.createNativeQuery("""
            INSERT INTO content_genre(id,content_id,genre_id)
            SELECT 3000000+id,id,:genre FROM content ORDER BY id LIMIT 5
            """).setParameter("genre", rare.getId()).executeUpdate();
        commitFullTextFixtures();
        var nativeRepository = new ContentSearchNativeRepository(entityManager, true);
        var conditions = List.of(condition(null, "드라마", null, 20), condition(null, "드라마", MediaType.TV, 20),
            condition(null, "전쟁", null, 20), condition(null, "공포", null, 20), condition("해리포터", null, null, 20),
            condition("해리포터", "드라마", MediaType.TV, 20),
            condition(null, "드라마", null, ContentSearchCursor.popular(100, 1049900L), 20),
            condition(null, "드라마", null, ContentSearchCursor.popular(0, 1L), 20));
        List<Integer> expectedSizes = List.of(21, 21, 5, 0, 21, 7, 21, 0);
        for (int index = 0; index < conditions.size(); index++) {
            ContentSearchCondition value = conditions.get(index);
            Long genreId = value.genreCode() == GenreCode.DRAMA ? drama.getId()
                : value.genreCode() == GenreCode.WAR ? rare.getId()
                : value.genreCode() == GenreCode.HORROR ? empty.getId() : null;
            var query = entityManager.createNativeQuery("EXPLAIN ANALYZE " + nativeRepository.searchSql(value, genreId));
            nativeRepository.bindParameters(query, value, genreId);
            String plan = String.valueOf(query.getSingleResult());
            System.out.println("search-normalization-plan[" + index + "]: " + plan);
            assertThat(plan).contains("actual time=");
            if (value.hasKeyword()) {
                assertThat(plan).contains("Union materialize with deduplication", "Table scan on candidates",
                    "idx_content_normalized_title_ko", "idx_content_normalized_title_en",
                    "Full-text index search on content using ft_content_search_title_ngram");
            } else {
                assertThat(plan).contains(value.mediaType() == null ? "idx_content_popular" : "idx_content_media_popular");
            }
            if (genreId != null) {
                assertThat(plan).contains("Single-row covering index lookup on cg using uk_content_genre");
            }
            long started = System.nanoTime();
            var results = contentQueryRepository.searchContents(value);
            System.out.println("search-normalization-repository-ms[" + index + "]: "
                + (System.nanoTime() - started) / 1_000_000.0);
            assertThat(results).hasSize(expectedSizes.get(index));
        }
    }

    private Genre persistGenre(String name) {
        Genre genre = Genre.create(name);
        entityManager.persist(genre);
        return genre;
    }

    private ContentSearchCondition condition(String keyword, String genre, MediaType media, int size) {
        return condition(keyword, genre, media, null, size);
    }

    private ContentSearchCondition condition(String keyword, String genre, MediaType media,
        ContentSearchCursor cursor, int size) {
        return ContentSearchCondition.of(keyword, genre, media, cursor, size);
    }

    private void commitFullTextFixtures() {
        entityManager.flush();
        entityManager.clear();
        TestTransaction.flagForCommit();
        TestTransaction.end();
        entityManager.createNativeQuery("ANALYZE TABLE content").getResultList();
    }
}
