package kr.flint.api.domain.search.repository;

import static kr.flint.api.common.query.CollectionQueryConditions.*;
import static kr.flint.bookmark.domain.QCollectionBookmark.*;
import static kr.flint.bookmark.domain.QContentBookmark.*;
import static kr.flint.collection.domain.QCollection.*;
import static kr.flint.user.domain.QUser.*;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.StringPath;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;

import kr.flint.api.common.query.ContentSearchKeyword;
import kr.flint.api.domain.search.dto.response.BookmarkedCollectionSearchRes;
import kr.flint.api.domain.search.dto.response.BookmarkedContentSearchRes;

@Repository
public class SearchQueryRepository {

    private final JPAQueryFactory jpaQueryFactory;
    private final EntityManager entityManager;
    private final boolean englishWordSearchEnabled;
    private final boolean singleCharacterInfixSearchEnabled;
    private final ProjectionFactory projectionFactory = new SpelAwareProxyProjectionFactory();

    public SearchQueryRepository(JPAQueryFactory jpaQueryFactory,
        EntityManager entityManager,
        @Value("${flint.content.english-word-search-enabled:false}") boolean englishWordSearchEnabled,
        @Value("${flint.content.single-character-infix-search-enabled:false}") boolean singleCharacterInfixSearchEnabled) {
        this.jpaQueryFactory = jpaQueryFactory;
        this.entityManager = entityManager;
        this.englishWordSearchEnabled = englishWordSearchEnabled;
        this.singleCharacterInfixSearchEnabled = singleCharacterInfixSearchEnabled;
    }

    /**
     * 북마크한 컬렉션에서 제목으로 검색
     */
    public List<BookmarkedCollectionSearchRes> searchBookmarkedCollections(
        final Long userId,
        final String keyword,
        final Long cursor,
        final int size
    ) {
        return jpaQueryFactory
            .select(Projections.constructor(
                BookmarkedCollectionSearchRes.class,
                collectionBookmark.id,
                collection.id,
                collection.image,
                collection.title,
                collection.description,
                collection.bookmarkCount,
                collection.userId,
                user.nickname,
                user.profileImage
            ))
            .from(collectionBookmark)
            .join(collection).on(collection.id.eq(collectionBookmark.collectionId))
            .join(user).on(user.id.eq(collection.userId))
            .where(
                    collectionBookmark.userId.eq(userId),
                    isVisibleCollection(),
                    containsKeyword(keyword, collection.title),
                cursor != null ? collectionBookmark.id.lt(cursor) : null
            )
            .orderBy(collectionBookmark.id.desc())
            .limit(size + 1L)
            .fetch();
    }

    /**
     * 북마크한 콘텐츠에서 제목으로 검색
     */
    @Transactional(readOnly = true)
    public List<BookmarkedContentSearchRes> searchBookmarkedContents(
        final Long userId,
        final String keyword,
        final Long cursor,
        final int size
    ) {
        ContentSearchKeyword prepared = ContentSearchKeyword.ofNullable(keyword);
        StringBuilder sql = new StringBuilder("""
            SELECT cb.id AS bookmarkId,c.id AS contentId,c.title AS title,c.author AS author,
                c.poster AS posterUrl,c.year AS year
            FROM content_bookmark cb JOIN content c ON c.id=cb.content_id
            """);
        if (prepared != null) {
            sql.append(" JOIN content_search_document d ON d.content_id=cb.content_id");
        }
        sql.append(" WHERE cb.user_id=:userId");
        if (cursor != null) sql.append(" AND cb.id<:cursor");
        if (prepared != null) sql.append(" AND ").append(matchesContentSql(prepared));
        sql.append(" ORDER BY cb.id DESC LIMIT :queryLimit");
        Query query = entityManager.createNativeQuery(sql.toString(), Tuple.class)
            .setParameter("userId", userId).setParameter("queryLimit", size + 1);
        if (cursor != null) query.setParameter("cursor", cursor);
        if (prepared != null) bindKeyword(query, prepared);
        List<?> rows = query.getResultList();
        return rows.stream().map(row -> {
            Tuple tuple = (Tuple) row;
            Map<String, Object> values = new HashMap<>();
            tuple.getElements().forEach(element -> values.put(element.getAlias(), tuple.get(element)));
            return projectionFactory.createProjection(BookmarkedContentSearchProjection.class, values).toResponse();
        }).toList();
    }

    private String matchesContentSql(ContentSearchKeyword value) {
        if (value.isSingleCharacter()) {
            if (singleCharacterInfixSearchEnabled) {
                return "(d.normalized_title_ko LIKE :characterPattern COLLATE utf8mb4_0900_bin"
                    + " OR d.normalized_title_en LIKE :characterPattern COLLATE utf8mb4_0900_bin)";
            }
            return "(d.normalized_title_ko=:normalizedKeyword OR d.normalized_title_en=:normalizedKeyword)";
        }
        if (value.usesEnglishWords(englishWordSearchEnabled)) {
            return """
                (d.word_title_ko=:wordKeyword OR d.word_title_en=:wordKeyword
                 OR (MATCH(d.word_title_ko,d.word_title_en) AGAINST (:fullTextKeyword IN BOOLEAN MODE)>0
                   AND (LOCATE(CONCAT(' ',:wordKeyword,' '),CONCAT(' ',d.word_title_ko,' '))>0
                     OR LOCATE(CONCAT(' ',:wordKeyword,' '),CONCAT(' ',d.word_title_en,' '))>0)))
                """;
        }
        return """
            (d.normalized_title_ko=:normalizedKeyword OR d.normalized_title_en=:normalizedKeyword
             OR (MATCH(d.search_title) AGAINST (:fullTextKeyword IN BOOLEAN MODE)>0
               AND (LOCATE(:normalizedKeyword,d.normalized_title_ko)>0
                 OR LOCATE(:normalizedKeyword,d.normalized_title_en)>0)))
            """;
    }

    private void bindKeyword(Query query, ContentSearchKeyword value) {
        if (value.isSingleCharacter() && singleCharacterInfixSearchEnabled) {
            query.setParameter("characterPattern", "%" + value.normalized() + "%");
        } else if (value.usesEnglishWords(englishWordSearchEnabled) && !value.isSingleCharacter()) {
            query.setParameter("wordKeyword", value.wordKey());
            query.setParameter("fullTextKeyword", value.wordPhraseQuery());
        } else {
            query.setParameter("normalizedKeyword", value.normalized());
            if (!value.isSingleCharacter()) query.setParameter("fullTextKeyword", value.booleanQuery(true));
        }
    }

    private BooleanBuilder containsKeyword(final String keyword, final StringPath field) {
        BooleanBuilder builder = new BooleanBuilder();
        if (keyword == null || keyword.isBlank()) {
            return builder;
        }
        return builder.and(field.containsIgnoreCase(keyword));
    }
}
