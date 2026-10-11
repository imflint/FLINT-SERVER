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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.StringPath;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;

import kr.flint.api.common.query.ContentSearchKeyword;
import kr.flint.api.domain.search.dto.response.BookmarkedCollectionSearchRes;
import kr.flint.api.domain.search.dto.response.BookmarkedContentSearchRes;

@Repository
public class SearchQueryRepository {

    private final JPAQueryFactory jpaQueryFactory;
    private final boolean localizedSearchEnabled;
    private final boolean englishWordSearchEnabled;
    private final boolean singleCharacterInfixSearchEnabled;

    @Autowired
    public SearchQueryRepository(JPAQueryFactory jpaQueryFactory,
        @Value("${flint.content.localized-search-enabled:false}") boolean localizedSearchEnabled,
        @Value("${flint.content.english-word-search-enabled:false}") boolean englishWordSearchEnabled,
        @Value("${flint.content.single-character-infix-search-enabled:false}") boolean singleCharacterInfixSearchEnabled) {
        this.jpaQueryFactory = jpaQueryFactory;
        this.localizedSearchEnabled = localizedSearchEnabled;
        this.englishWordSearchEnabled = englishWordSearchEnabled;
        this.singleCharacterInfixSearchEnabled = singleCharacterInfixSearchEnabled;
        if (singleCharacterInfixSearchEnabled && !localizedSearchEnabled) {
            throw new IllegalStateException("Single character search requires localized search");
        }
    }

    public SearchQueryRepository(JPAQueryFactory factory, boolean localized, boolean words) {
        this(factory, localized, words, false);
    }

    public SearchQueryRepository(JPAQueryFactory factory, boolean localized) {
        this(factory, localized, false);
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
                onCondition(prepared, this::matchesContent),
                onCondition(cursor, contentBookmark.id::lt)
            )
            .orderBy(contentBookmark.id.desc())
            .limit(size + 1L)
            .fetch();
    }

    private BooleanExpression matchesContent(ContentSearchKeyword value) {
        if (value.isSingleCharacter()) {
            if (singleCharacterInfixSearchEnabled) {
                String pattern = "%" + value.normalized() + "%";
                return Expressions.booleanTemplate("function('title_contains_binary', {0}, {1})",
                    content.normalizedTitleKo, pattern).isTrue()
                    .or(Expressions.booleanTemplate("function('title_contains_binary', {0}, {1})",
                        content.normalizedTitleEn, pattern).isTrue());
            }
            return localizedSearchEnabled
                ? content.normalizedTitleKo.eq(value.normalized()).or(content.normalizedTitleEn.eq(value.normalized()))
                : content.title.lower().eq(value.exactKey(false));
        }
        if (value.usesEnglishWords(englishWordSearchEnabled)) {
            String words = value.wordKey();
            return content.wordTitleKo.eq(words).or(content.wordTitleEn.eq(words))
                .or(Expressions.booleanTemplate("function('match_against_words_boolean', {0}, {1}, {2})",
                    content.wordTitleKo, content.wordTitleEn, value.wordPhraseQuery()).isTrue()
                    .and(content.wordTitleKo.prepend(" ").append(" ").contains(" " + words + " ")
                        .or(content.wordTitleEn.prepend(" ").append(" ").contains(" " + words + " "))));
        }
        return localizedSearchEnabled
            ? content.normalizedTitleKo.eq(value.normalized()).or(content.normalizedTitleEn.eq(value.normalized()))
                .or(Expressions.booleanTemplate("function('match_against_boolean', {0}, {1})",
                    content.searchTitle, value.booleanQuery(true)).isTrue()
                    .and(content.normalizedTitleKo.contains(value.normalized())
                        .or(content.normalizedTitleEn.contains(value.normalized()))))
            : content.title.containsIgnoreCase(value.raw());
    }

    private BooleanBuilder containsKeyword(final String keyword, final StringPath field) {
        BooleanBuilder builder = new BooleanBuilder();
        if (keyword == null || keyword.isBlank()) {
            return builder;
        }
        return builder.and(field.containsIgnoreCase(keyword));
    }
}
