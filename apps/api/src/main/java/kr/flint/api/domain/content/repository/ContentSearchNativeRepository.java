package kr.flint.api.domain.content.repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import kr.flint.api.domain.content.dto.ContentSearchCondition;
import kr.flint.api.common.query.ContentSearchKeyword;

@Repository
public class ContentSearchNativeRepository {
    private static final String CONTENT_COLUMNS = """
        c.id AS id, c.title AS title, c.author AS author, c.poster AS posterUrl,
        c.year AS year, c.bookmark_count AS bookmarkCount
        """;

    private final EntityManager entityManager;
    private final boolean localizedSearchEnabled;
    private final boolean searchReadModelEnabled;
    private final ProjectionFactory projectionFactory = new SpelAwareProxyProjectionFactory();

    @Autowired
    public ContentSearchNativeRepository(
        EntityManager entityManager,
        @Value("${flint.content.localized-search-enabled:false}") boolean localizedSearchEnabled,
        @Value("${flint.content.search-read-model-enabled:false}") boolean searchReadModelEnabled
    ) {
        this.entityManager = entityManager;
        this.localizedSearchEnabled = localizedSearchEnabled;
        this.searchReadModelEnabled = searchReadModelEnabled;
    }

    public ContentSearchNativeRepository(EntityManager entityManager, boolean localizedSearchEnabled) {
        this(entityManager, localizedSearchEnabled, false);
    }

    public List<ContentSearchProjection> search(ContentSearchCondition condition, Long genreId) {
        if (condition.hasKeyword() && condition.cursor() != null) {
            condition.cursor().validateKeywordVersion(localizedSearchEnabled);
        }
        Query query = entityManager.createNativeQuery(searchSql(condition, genreId), Tuple.class);
        bindParameters(query, condition, genreId);
        List<?> rows = query.getResultList();
        return rows.stream().map(row -> toProjection((Tuple) row)).toList();
    }

    public boolean localizedSearchEnabled() {
        return localizedSearchEnabled;
    }

    public List<ContentSearchProjection> searchAllKeywords(String keyword) {
        ContentSearchCondition condition = ContentSearchCondition.of(keyword, null, null, null, 0);
        Query query = entityManager.createNativeQuery(keywordSql(condition, null, false), Tuple.class);
        bindKeyword(query, condition);
        List<?> rows = query.getResultList();
        return rows.stream().map(row -> toProjection((Tuple) row)).toList();
    }

    String searchSql(ContentSearchCondition condition, Long genreId) {
        return condition.hasKeyword() ? keywordSql(condition, genreId) : popularSql(condition);
    }

    void bindParameters(Query query, ContentSearchCondition condition, Long genreId) {
        query.setParameter("queryLimit", condition.queryLimit());
        if (genreId != null) {
            query.setParameter("genreId", genreId);
        }
        if (condition.mediaType() != null) {
            query.setParameter("mediaType", condition.mediaType().name());
        }
        if (condition.hasKeyword()) {
            bindKeyword(query, condition);
        }
        if (condition.cursor() != null) {
            query.setParameter("cursorId", condition.cursor().contentId());
            if (condition.hasKeyword()) {
                query.setParameter("cursorRank", condition.cursor().exactMatchRank());
                query.setParameter("cursorScore", condition.cursor().relevanceScore());
            } else {
                query.setParameter("cursorCount", condition.cursor().bookmarkCount());
            }
        }
    }

    private void bindKeyword(Query query, ContentSearchCondition condition) {
        ContentSearchKeyword keyword = ContentSearchKeyword.ofNullable(condition.keyword());
        query.setParameter("normalizedKeyword", keyword.exactKey(localizedSearchEnabled));
        query.setParameter("fullTextKeyword", keyword.booleanQuery(localizedSearchEnabled));
        if (!localizedSearchEnabled) {
            query.setParameter("scoreKeyword", keyword.scoreQuery(false));
        }
    }

    // Preserve index order through the join so LIMIT stops after matching size + 1 rows.
    private String popularSql(ContentSearchCondition condition) {
        String index = condition.mediaType() == null ? "idx_content_popular" : "idx_content_media_popular";
        StringBuilder sql = new StringBuilder("SELECT ").append(CONTENT_COLUMNS)
            .append(", 0 AS exactMatchRank, 0.0 AS relevanceScore FROM content c FORCE INDEX (")
            .append(index).append(") STRAIGHT_JOIN content_genre cg FORCE INDEX (uk_content_genre)")
            .append(" ON cg.content_id=c.id AND cg.genre_id=:genreId WHERE 1=1");
        appendMediaFilter(sql, condition);
        if (condition.cursor() != null) {
            sql.append(" AND (c.bookmark_count<:cursorCount OR (c.bookmark_count=:cursorCount AND c.id<:cursorId))");
        }
        return sql.append(" ORDER BY c.bookmark_count DESC, c.id DESC LIMIT :queryLimit").toString();
    }

    // UNION and ID aggregation preserve exact hits and scores without truncating candidates.
    private String keywordSql(ContentSearchCondition condition, Long genreId) {
        return keywordSql(condition, genreId, true);
    }

    private String keywordSql(ContentSearchCondition condition, Long genreId, boolean paginated) {
        if (localizedSearchEnabled && searchReadModelEnabled) {
            return readModelKeywordSql(condition, genreId, paginated);
        }
        String title = localizedSearchEnabled ? "search_title" : "title";
        String exactMatch = localizedSearchEnabled
            ? "c.normalized_title_ko=:normalizedKeyword OR c.normalized_title_en=:normalizedKeyword"
            : "LOWER(c.title)=:normalizedKeyword";
        String candidates = localizedSearchEnabled ? """
            SELECT id, MAX(relevanceScore) AS relevanceScore FROM (
            SELECT id, 0.0 AS relevanceScore FROM content FORCE INDEX (idx_content_normalized_title_ko)
            WHERE normalized_title_ko=:normalizedKeyword
            UNION SELECT id, 0.0 AS relevanceScore FROM content FORCE INDEX (idx_content_normalized_title_en)
            WHERE normalized_title_en=:normalizedKeyword
            UNION SELECT id, MATCH(search_title) AGAINST (:fullTextKeyword IN BOOLEAN MODE) AS relevanceScore
            FROM content WHERE MATCH(search_title) AGAINST (:fullTextKeyword IN BOOLEAN MODE)>0
            ) hits GROUP BY id
            """ : """
            SELECT id FROM content FORCE INDEX (idx_content_title_lower)
            WHERE LOWER(title)=:normalizedKeyword
            """;
        if (!localizedSearchEnabled) {
            candidates += " UNION SELECT id FROM content WHERE MATCH(title) AGAINST (:fullTextKeyword IN BOOLEAN MODE)>0";
        }
        String score = localizedSearchEnabled ? "candidates.relevanceScore"
            : "MATCH(c." + title + ") AGAINST (:scoreKeyword IN NATURAL LANGUAGE MODE)";
        StringBuilder sql = new StringBuilder("SELECT ranked.* FROM (SELECT ").append(CONTENT_COLUMNS)
            .append(", CASE WHEN ").append(exactMatch).append(" THEN 0 ELSE 1 END AS exactMatchRank,")
            .append(score).append(" AS relevanceScore FROM (").append(candidates)
            .append(") candidates STRAIGHT_JOIN content c ON c.id=candidates.id");
        if (genreId != null) {
            sql.append(" STRAIGHT_JOIN content_genre cg FORCE INDEX (uk_content_genre)")
                .append(" ON cg.content_id=c.id AND cg.genre_id=:genreId");
        }
        sql.append(" WHERE 1=1");
        if (localizedSearchEnabled) {
            // AND finds all bigrams; substring verification rejects reordered or split-token matches.
            sql.append(" AND (LOCATE(:normalizedKeyword,c.normalized_title_ko)>0")
                .append(" OR LOCATE(:normalizedKeyword,c.normalized_title_en)>0)");
        }
        appendMediaFilter(sql, condition);
        sql.append(") ranked");
        if (condition.cursor() != null) {
            sql.append("""
                 WHERE (ranked.exactMatchRank>:cursorRank
                    OR (ranked.exactMatchRank=:cursorRank AND ranked.relevanceScore<:cursorScore)
                    OR (ranked.exactMatchRank=:cursorRank AND ranked.relevanceScore=:cursorScore AND ranked.id<:cursorId))
                """);
        }
        sql.append(" ORDER BY ranked.exactMatchRank, ranked.relevanceScore DESC, ranked.id DESC");
        if (paginated) {
            sql.append(" LIMIT :queryLimit");
        }
        return sql.toString();
    }

    // FTS_DOC_ID and rank are covered by FULLTEXT; hydrate wide content rows only after page selection.
    private String readModelKeywordSql(ContentSearchCondition condition, Long genreId, boolean paginated) {
        StringBuilder sql = new StringBuilder("SELECT ").append(CONTENT_COLUMNS)
            .append(", page.exactMatchRank, page.relevanceScore FROM (SELECT ranked.* FROM (")
            .append("""
                SELECT d.content_id AS id,
                    CASE WHEN d.normalized_title_ko=:normalizedKeyword OR d.normalized_title_en=:normalizedKeyword
                        THEN 0 ELSE 1 END AS exactMatchRank, candidates.relevanceScore
                FROM (
                    SELECT FTS_DOC_ID, MAX(relevanceScore) AS relevanceScore FROM (
                        SELECT FTS_DOC_ID, 0.0 AS relevanceScore FROM content_search_document
                            FORCE INDEX (idx_search_document_title_ko) WHERE normalized_title_ko=:normalizedKeyword
                        UNION SELECT FTS_DOC_ID, 0.0 AS relevanceScore FROM content_search_document
                            FORCE INDEX (idx_search_document_title_en) WHERE normalized_title_en=:normalizedKeyword
                        UNION SELECT FTS_DOC_ID, MATCH(search_title) AGAINST (:fullTextKeyword IN BOOLEAN MODE) AS relevanceScore
                            FROM content_search_document WHERE MATCH(search_title) AGAINST (:fullTextKeyword IN BOOLEAN MODE)>0
                    ) hits GROUP BY FTS_DOC_ID
                ) candidates STRAIGHT_JOIN content_search_document d ON d.FTS_DOC_ID=candidates.FTS_DOC_ID
                """);
        if (genreId != null) {
            sql.append(" STRAIGHT_JOIN content_genre cg FORCE INDEX (uk_content_genre)")
                .append(" ON cg.content_id=d.content_id AND cg.genre_id=:genreId");
        }
        sql.append(" WHERE (LOCATE(:normalizedKeyword,d.normalized_title_ko)>0")
            .append(" OR LOCATE(:normalizedKeyword,d.normalized_title_en)>0)");
        if (condition.mediaType() != null) {
            sql.append(" AND d.media_type=:mediaType");
        }
        sql.append(") ranked");
        if (condition.cursor() != null) {
            sql.append("""
                 WHERE (ranked.exactMatchRank>:cursorRank
                    OR (ranked.exactMatchRank=:cursorRank AND ranked.relevanceScore<:cursorScore)
                    OR (ranked.exactMatchRank=:cursorRank AND ranked.relevanceScore=:cursorScore AND ranked.id<:cursorId))
                """);
        }
        sql.append(" ORDER BY ranked.exactMatchRank, ranked.relevanceScore DESC, ranked.id DESC");
        if (paginated) {
            sql.append(" LIMIT :queryLimit");
        }
        return sql.append(") page STRAIGHT_JOIN content c ON c.id=page.id")
            .append(" ORDER BY page.exactMatchRank, page.relevanceScore DESC, page.id DESC").toString();
    }

    private void appendMediaFilter(StringBuilder sql, ContentSearchCondition condition) {
        if (condition.mediaType() != null) {
            sql.append(" AND c.media_type=:mediaType");
        }
    }

    private ContentSearchProjection toProjection(Tuple tuple) {
        Map<String, Object> values = new HashMap<>();
        tuple.getElements().forEach(element -> values.put(element.getAlias(), tuple.get(element)));
        return projectionFactory.createProjection(ContentSearchProjection.class, values);
    }

}
