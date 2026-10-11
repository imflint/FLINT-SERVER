package kr.flint.api.domain.content.repository;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import kr.flint.api.domain.content.dto.ContentSearchCondition;
import kr.flint.api.common.query.ContentSearchKeyword;

@Repository
public class ContentSearchNativeRepository {
    private static final int SMALL_GENRE_LIMIT = 2048;
    private static final String CONTENT_COLUMNS = """
        c.id AS id, c.title AS title, c.author AS author, c.poster AS posterUrl,
        c.year AS year, c.bookmark_count AS bookmarkCount
        """;

    private final EntityManager entityManager;
    private final boolean localizedSearchEnabled;
    private final boolean searchReadModelEnabled;
    private final boolean englishWordSearchEnabled;
    private final boolean singleCharacterInfixSearchEnabled;
    private final ProjectionFactory projectionFactory = new SpelAwareProxyProjectionFactory();

    @Autowired
    public ContentSearchNativeRepository(
        EntityManager entityManager,
        @Value("${flint.content.localized-search-enabled:false}") boolean localizedSearchEnabled,
        @Value("${flint.content.search-read-model-enabled:false}") boolean searchReadModelEnabled,
        @Value("${flint.content.english-word-search-enabled:false}") boolean englishWordSearchEnabled,
        @Value("${flint.content.single-character-infix-search-enabled:false}") boolean singleCharacterInfixSearchEnabled
    ) {
        this.entityManager = entityManager;
        this.localizedSearchEnabled = localizedSearchEnabled;
        this.searchReadModelEnabled = searchReadModelEnabled;
        this.englishWordSearchEnabled = englishWordSearchEnabled;
        this.singleCharacterInfixSearchEnabled = singleCharacterInfixSearchEnabled;
        if (englishWordSearchEnabled && (!localizedSearchEnabled || !searchReadModelEnabled)) {
            throw new IllegalStateException("English word search requires localized search and the search read model");
        }
        if (singleCharacterInfixSearchEnabled && (!localizedSearchEnabled || !searchReadModelEnabled)) {
            throw new IllegalStateException("Single character search requires localized search and the search read model");
        }
    }

    public ContentSearchNativeRepository(EntityManager entityManager, boolean localized, boolean readModel, boolean words) {
        this(entityManager, localized, readModel, words, false);
    }

    public ContentSearchNativeRepository(EntityManager entityManager, boolean localized, boolean readModel) {
        this(entityManager, localized, readModel, false);
    }

    public ContentSearchNativeRepository(EntityManager entityManager, boolean localizedSearchEnabled) {
        this(entityManager, localizedSearchEnabled, false);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<ContentSearchProjection> search(ContentSearchCondition condition, Long genreId) {
        if (condition.hasKeyword() && condition.cursor() != null) {
            condition.cursor().validateKeywordVersion(localizedSearchEnabled, searchReadModelEnabled,
                usesEnglishWords(condition.keyword()), usesSingleCharacterInfix(condition.keyword()));
        }
        if (usesSingleCharacterInfix(condition.keyword())) {
            return searchSingleCharacter(condition, genreId, condition.queryLimit());
        }
        Query query = entityManager.createNativeQuery(searchSql(condition, genreId), Tuple.class);
        bindParameters(query, condition, genreId);
        List<?> rows = query.getResultList();
        if (condition.hasKeyword() && localizedSearchEnabled && searchReadModelEnabled) {
            return hydratePage(rows);
        }
        return rows.stream().map(row -> toProjection((Tuple) row)).toList();
    }

    public boolean localizedSearchEnabled() {
        return localizedSearchEnabled;
    }

    public boolean searchReadModelEnabled() {
        return localizedSearchEnabled && searchReadModelEnabled;
    }

    public boolean usesEnglishWords(String keyword) {
        ContentSearchKeyword prepared = ContentSearchKeyword.ofNullable(keyword);
        return prepared != null && prepared.usesEnglishWords(englishWordSearchEnabled);
    }

    public boolean usesSingleCharacterInfix(String keyword) {
        ContentSearchKeyword prepared = ContentSearchKeyword.ofNullable(keyword);
        return singleCharacterInfixSearchEnabled && prepared != null && prepared.isSingleCharacter();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<ContentSearchProjection> searchAllKeywords(String keyword) {
        ContentSearchCondition condition = ContentSearchCondition.of(keyword, null, null, null, 0);
        if (usesSingleCharacterInfix(keyword)) {
            return searchSingleCharacter(condition, null, 50);
        }
        Query query = entityManager.createNativeQuery(keywordSql(condition, null, false), Tuple.class);
        bindKeyword(query, condition);
        if (ContentSearchKeyword.ofNullable(keyword).isSingleCharacter()) {
            query.setMaxResults(50);
        }
        List<?> rows = query.getResultList();
        return rows.stream().map(row -> toProjection((Tuple) row)).toList();
    }

    // Separate exact hits from LIKE scans so each read stops once the page is filled.
    private List<ContentSearchProjection> searchSingleCharacter(ContentSearchCondition condition, Long genreId, int limit) {
        List<Object> rows = new ArrayList<>();
        int firstRank = condition.cursor() == null ? 0 : condition.cursor().exactMatchRank();
        for (int rank = firstRank; rank <= 1 && rows.size() < limit; rank++) {
            Query query = entityManager.createNativeQuery(singleCharacterSql(condition, genreId, rank), Tuple.class);
            bindSingleCharacterParameters(query, condition, genreId, rank, limit - rows.size());
            rows.addAll(query.getResultList());
        }
        return hydratePage(rows);
    }

    String singleCharacterSql(ContentSearchCondition condition, Long genreId, int rank) {
        StringBuilder sql = new StringBuilder("SELECT d.content_id AS id, ").append(rank)
            .append(" AS exactMatchRank, 0.0 AS relevanceScore FROM content_search_document d");
        if (rank == 1) sql.append(" FORCE INDEX (uk_search_document_content)");
        if (genreId != null) {
            sql.append(" STRAIGHT_JOIN content_genre cg FORCE INDEX (uk_content_genre)")
                .append(" ON cg.content_id=d.content_id AND cg.genre_id=:genreId");
        }
        if (rank == 0) {
            sql.append(" WHERE (d.normalized_title_ko=:normalizedKeyword OR d.normalized_title_en=:normalizedKeyword)")
                .append(" AND (d.normalized_title_ko=:normalizedKeyword COLLATE utf8mb4_0900_bin")
                .append(" OR d.normalized_title_en=:normalizedKeyword COLLATE utf8mb4_0900_bin)");
        } else {
            sql.append(" WHERE (d.normalized_title_ko LIKE :characterPattern COLLATE utf8mb4_0900_bin")
                .append(" OR d.normalized_title_en LIKE :characterPattern COLLATE utf8mb4_0900_bin)")
                .append(" AND NOT (d.normalized_title_ko <=> :normalizedKeyword COLLATE utf8mb4_0900_bin)")
                .append(" AND NOT (d.normalized_title_en <=> :normalizedKeyword COLLATE utf8mb4_0900_bin)");
        }
        if (condition.mediaType() != null) sql.append(" AND d.media_type=:mediaType");
        if (condition.cursor() != null && rank == condition.cursor().exactMatchRank()) {
            sql.append(" AND d.content_id<:cursorId");
        }
        return sql.append(" ORDER BY d.content_id DESC LIMIT :queryLimit").toString();
    }

    void bindSingleCharacterParameters(Query query, ContentSearchCondition condition, Long genreId, int rank, int limit) {
        String keyword = ContentSearchKeyword.ofNullable(condition.keyword()).normalized();
        query.setParameter("normalizedKeyword", keyword);
        if (rank == 1) query.setParameter("characterPattern", "%" + keyword + "%");
        query.setParameter("queryLimit", limit);
        if (genreId != null) query.setParameter("genreId", genreId);
        if (condition.mediaType() != null) query.setParameter("mediaType", condition.mediaType().name());
        if (condition.cursor() != null && rank == condition.cursor().exactMatchRank()) {
            query.setParameter("cursorId", condition.cursor().contentId());
        }
    }

    String searchSql(ContentSearchCondition condition, Long genreId) {
        if (condition.hasKeyword()) {
            return keywordSql(condition, genreId);
        }
        return genreId != null && isSmallGenre(genreId) ? genreFirstPopularSql(condition) : popularSql(condition);
    }

    // The bounded probe selects only an access path, never truncates the search results.
    private boolean isSmallGenre(Long genreId) {
        Number count = (Number) entityManager.createNativeQuery("""
            SELECT COUNT(*) FROM (
                SELECT content_id FROM content_genre FORCE INDEX (idx_content_genre_genre_content)
                WHERE genre_id=:genreId LIMIT :probeLimit
            ) membership
            """)
            .setParameter("genreId", genreId)
            .setParameter("probeLimit", SMALL_GENRE_LIMIT + 1)
            .getSingleResult();
        return count.longValue() <= SMALL_GENRE_LIMIT;
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
        if (keyword.isSingleCharacter()) {
            return;
        }
        boolean words = keyword.usesEnglishWords(englishWordSearchEnabled);
        query.setParameter("fullTextKeyword", words ? keyword.wordPhraseQuery() : keyword.booleanQuery(localizedSearchEnabled));
        if (words) {
            query.setParameter("wordKeyword", keyword.wordKey());
        }
        if (!localizedSearchEnabled) {
            query.setParameter("scoreKeyword", keyword.scoreQuery(false));
        }
    }

    // Keep popularity scans covering and load wide rows only for the selected page.
    private String popularSql(ContentSearchCondition condition) {
        String index = condition.mediaType() == null ? "idx_content_popular" : "idx_content_media_popular";
        StringBuilder sql = new StringBuilder("SELECT ").append(CONTENT_COLUMNS)
            .append(", 0 AS exactMatchRank, 0.0 AS relevanceScore FROM (SELECT c.id, c.bookmark_count FROM content c FORCE INDEX (")
            .append(index).append(") STRAIGHT_JOIN content_genre cg FORCE INDEX (uk_content_genre)")
            .append(" ON cg.content_id=c.id AND cg.genre_id=:genreId WHERE 1=1");
        return finishPopularPage(sql, condition);
    }

    private String genreFirstPopularSql(ContentSearchCondition condition) {
        StringBuilder sql = new StringBuilder("SELECT ").append(CONTENT_COLUMNS)
            .append(", 0 AS exactMatchRank, 0.0 AS relevanceScore FROM (SELECT c.id, c.bookmark_count")
            .append(" FROM content_genre cg FORCE INDEX (idx_content_genre_genre_content)")
            .append(" STRAIGHT_JOIN content c ON c.id=cg.content_id WHERE cg.genre_id=:genreId");
        return finishPopularPage(sql, condition);
    }

    private String finishPopularPage(StringBuilder sql, ContentSearchCondition condition) {
        appendMediaFilter(sql, condition);
        if (condition.cursor() != null) {
            sql.append(" AND (c.bookmark_count<:cursorCount OR (c.bookmark_count=:cursorCount AND c.id<:cursorId))");
        }
        return sql.append(" ORDER BY c.bookmark_count DESC, c.id DESC LIMIT :queryLimit")
            .append(") page STRAIGHT_JOIN content c ON c.id=page.id")
            .append(" ORDER BY page.bookmark_count DESC, page.id DESC").toString();
    }

    // UNION and ID aggregation preserve exact hits and scores without truncating candidates.
    private String keywordSql(ContentSearchCondition condition, Long genreId) {
        return keywordSql(condition, genreId, true);
    }

    private String keywordSql(ContentSearchCondition condition, Long genreId, boolean paginated) {
        if (localizedSearchEnabled && searchReadModelEnabled) {
            return readModelKeywordSql(condition, genreId, paginated);
        }
        boolean exactOnly = ContentSearchKeyword.ofNullable(condition.keyword()).isSingleCharacter();
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
            """ : """
            SELECT id FROM content FORCE INDEX (idx_content_title_lower)
            WHERE LOWER(title)=:normalizedKeyword
            """;
        if (localizedSearchEnabled) {
            if (!exactOnly) {
                candidates += """
                    UNION SELECT id, MATCH(search_title) AGAINST (:fullTextKeyword IN BOOLEAN MODE) AS relevanceScore
                    FROM content WHERE MATCH(search_title) AGAINST (:fullTextKeyword IN BOOLEAN MODE)>0
                    """;
            }
            candidates += ") hits GROUP BY id";
        } else if (!exactOnly) {
            candidates += " UNION SELECT id FROM content WHERE MATCH(title) AGAINST (:fullTextKeyword IN BOOLEAN MODE)>0";
        }
        String score = exactOnly ? "0.0" : localizedSearchEnabled ? "candidates.relevanceScore"
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
        if (localizedSearchEnabled && !exactOnly) {
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

    // Derived MATCH scores disable FTS covering. Union only document IDs and score outside the derived table.
    private String readModelKeywordSql(ContentSearchCondition condition, Long genreId, boolean paginated) {
        boolean exactOnly = ContentSearchKeyword.ofNullable(condition.keyword()).isSingleCharacter();
        boolean words = usesEnglishWords(condition.keyword());
        String fields = words ? "word_title_ko,word_title_en" : "search_title";
        String score = exactOnly ? "0.0" : "MATCH(" + (words ? "d.word_title_ko,d.word_title_en" : "d.search_title")
            + ") AGAINST (:fullTextKeyword IN BOOLEAN MODE)";
        String rank = exactOnly ? "0" : words
            ? "CASE WHEN d.word_title_ko=:wordKeyword OR d.word_title_en=:wordKeyword THEN 0 ELSE 1 END"
            : "CASE WHEN d.normalized_title_ko=:normalizedKeyword OR d.normalized_title_en=:normalizedKeyword THEN 0 ELSE 1 END";
        String exactKo = words && !exactOnly ? " AND word_title_ko=:wordKeyword" : "";
        String exactEn = words && !exactOnly ? " AND word_title_en=:wordKeyword" : "";
        String candidates = """
            SELECT FTS_DOC_ID FROM content_search_document FORCE INDEX (idx_search_document_title_ko)
                WHERE normalized_title_ko=:normalizedKeyword%s
            UNION SELECT FTS_DOC_ID FROM content_search_document FORCE INDEX (idx_search_document_title_en)
                WHERE normalized_title_en=:normalizedKeyword%s
            """.formatted(exactKo, exactEn);
        if (!exactOnly) {
            candidates += """
                UNION SELECT FTS_DOC_ID FROM content_search_document
                    WHERE MATCH(%s) AGAINST (:fullTextKeyword IN BOOLEAN MODE)>0
                """.formatted(fields);
        }
        String indexes = exactOnly ? "idx_search_document_rank"
            : "idx_search_document_rank, " + (words ? "ft_search_document_words" : "ft_search_document_title");
        StringBuilder sql = new StringBuilder("SELECT ")
            .append(paginated ? "d.content_id AS id" : CONTENT_COLUMNS)
            .append(", ").append(rank).append(" AS exactMatchRank, ").append(score).append(" AS relevanceScore FROM (")
            .append(candidates)
            .append("""
                ) candidates STRAIGHT_JOIN content_search_document d FORCE INDEX (%s)
                    ON d.FTS_DOC_ID=candidates.FTS_DOC_ID
                """.formatted(indexes));
        if (genreId != null) {
            sql.append(" STRAIGHT_JOIN content_genre cg FORCE INDEX (uk_content_genre)")
                .append(" ON cg.content_id=d.content_id AND cg.genre_id=:genreId");
        }
        if (!paginated) {
            sql.append(" STRAIGHT_JOIN content c ON c.id=d.content_id");
        }
        if (exactOnly) {
            sql.append(" WHERE 1=1");
        } else if (words) {
            // Space padding validates whole words within one title, not across title columns.
            sql.append(" WHERE (LOCATE(CONCAT(' ',:wordKeyword,' '),CONCAT(' ',d.word_title_ko,' '))>0")
                .append(" OR LOCATE(CONCAT(' ',:wordKeyword,' '),CONCAT(' ',d.word_title_en,' '))>0)");
        } else {
            sql.append(" WHERE (LOCATE(:normalizedKeyword,d.normalized_title_ko)>0")
                .append(" OR LOCATE(:normalizedKeyword,d.normalized_title_en)>0)");
        }
        if (condition.mediaType() != null) {
            sql.append(" AND d.media_type=:mediaType");
        }
        if (condition.cursor() != null) {
            sql.append(" AND (").append(rank).append(">:cursorRank OR (").append(rank)
                .append("=:cursorRank AND ").append(score).append("<:cursorScore) OR (")
                .append(rank).append("=:cursorRank AND ").append(score)
                .append("=:cursorScore AND d.content_id<:cursorId))");
        }
        return sql.append(" ORDER BY exactMatchRank, relevanceScore DESC, id DESC")
            .append(paginated ? " LIMIT :queryLimit" : "").toString();
    }

    private List<ContentSearchProjection> hydratePage(List<?> rankedRows) {
        if (rankedRows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rankedRows.stream().map(row -> ((Number) ((Tuple) row).get("id")).longValue()).toList();
        List<?> contents = entityManager.createNativeQuery("SELECT " + CONTENT_COLUMNS + " FROM content c WHERE c.id IN (:ids)", Tuple.class)
            .setParameter("ids", ids).getResultList();
        Map<Long, Tuple> byId = new HashMap<>();
        contents.forEach(row -> byId.put(((Number) ((Tuple) row).get("id")).longValue(), (Tuple) row));
        return rankedRows.stream().map(row -> {
            Tuple rank = (Tuple) row;
            Tuple content = byId.get(((Number) rank.get("id")).longValue());
            Map<String, Object> values = new HashMap<>();
            content.getElements().forEach(element -> values.put(element.getAlias(), content.get(element)));
            values.put("exactMatchRank", rank.get("exactMatchRank"));
            values.put("relevanceScore", rank.get("relevanceScore"));
            return projectionFactory.createProjection(ContentSearchProjection.class, values);
        }).toList();
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
