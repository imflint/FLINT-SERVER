package kr.flint.api.domain.search.repository;

import static kr.flint.bookmark.domain.QCollectionBookmark.*;
import static kr.flint.bookmark.domain.QContentBookmark.*;
import static kr.flint.collection.domain.QCollection.*;
import static kr.flint.content.domain.QContent.*;
import static kr.flint.user.domain.QUser.*;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.StringPath;
import com.querydsl.jpa.impl.JPAQueryFactory;

import kr.flint.api.domain.search.dto.response.BookmarkedCollectionSearchRes;
import kr.flint.api.domain.search.dto.response.BookmarkedContentSearchRes;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class SearchQueryRepository {

	private final JPAQueryFactory jpaQueryFactory;

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
				containsKeyword(keyword, content.title),
				cursor != null ? contentBookmark.id.lt(cursor) : null
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
