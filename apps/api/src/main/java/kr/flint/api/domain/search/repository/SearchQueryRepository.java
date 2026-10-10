package kr.flint.api.domain.search.repository;

import static kr.flint.api.common.query.CollectionQueryConditions.*;
import static kr.flint.bookmark.domain.QCollectionBookmark.*;
import static kr.flint.bookmark.domain.QContentBookmark.*;
import static kr.flint.collection.domain.QCollection.*;
import static kr.flint.content.domain.QContent.*;
import static kr.flint.user.domain.QUser.*;
import static kr.flint.shared.util.QueryDslUtil.onCondition;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.StringPath;
import com.querydsl.jpa.impl.JPAQueryFactory;

import kr.flint.api.common.query.ContentSearchKeyword;
import kr.flint.api.domain.search.dto.response.BookmarkedCollectionSearchRes;
import kr.flint.api.domain.search.dto.response.BookmarkedContentSearchRes;

@Repository
public class SearchQueryRepository {

    private final JPAQueryFactory jpaQueryFactory;
    private final boolean localizedSearchEnabled;

    public SearchQueryRepository(JPAQueryFactory jpaQueryFactory,
        @Value("${flint.content.localized-search-enabled:false}") boolean localizedSearchEnabled) {
        this.jpaQueryFactory = jpaQueryFactory;
        this.localizedSearchEnabled = localizedSearchEnabled;
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
    public List<BookmarkedContentSearchRes> searchBookmarkedContents(
        final Long userId,
        final String keyword,
        final Long cursor,
        final int size
    ) {
        ContentSearchKeyword prepared = ContentSearchKeyword.ofNullable(keyword);
        return jpaQueryFactory
            .select(Projections.constructor(
                BookmarkedContentSearchRes.class,
                contentBookmark.id,
                content.id,
                content.title,
                content.author,
                content.poster,
                content.year
            ))
            .from(contentBookmark)
            .join(content).on(content.id.eq(contentBookmark.contentId))
            .where(
                contentBookmark.userId.eq(userId),
                onCondition(prepared, value -> localizedSearchEnabled
                    ? content.normalizedTitleKo.eq(value.normalized())
                        .or(content.normalizedTitleEn.eq(value.normalized()))
                        .or(Expressions.booleanTemplate("function('match_against_boolean', {0}, {1})",
                            content.searchTitle, value.booleanQuery(true)).isTrue()
                            .and(content.normalizedTitleKo.contains(value.normalized())
                                .or(content.normalizedTitleEn.contains(value.normalized()))))
                    : content.title.containsIgnoreCase(value.raw())),
                onCondition(cursor, contentBookmark.id::lt)
            )
            .orderBy(contentBookmark.id.desc())
            .limit(size + 1L)
            .fetch();
    }

    private BooleanBuilder containsKeyword(final String keyword, final StringPath field) {
        BooleanBuilder builder = new BooleanBuilder();
        if (keyword == null || keyword.isBlank()) {
            return builder;
        }
        return builder.and(field.containsIgnoreCase(keyword));
    }
}
