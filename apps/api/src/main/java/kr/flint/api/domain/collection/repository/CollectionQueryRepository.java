package kr.flint.api.domain.collection.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;

import kr.flint.api.domain.collection.dto.response.GetCollectionDetailListRes;
import kr.flint.api.domain.collection.dto.response.GetCollectionDetailRes;
import kr.flint.api.domain.collection.dto.response.GetCollectionSimpleRes;
import lombok.RequiredArgsConstructor;

import static kr.flint.bookmark.domain.QContentBookmark.*;
import static kr.flint.collection.domain.QCollection.collection;
import static kr.flint.collection.domain.QCollectionContent.*;
import static kr.flint.collection.domain.QRecentViewedCollection.*;
import static kr.flint.user.domain.QUser.user;
import static kr.flint.bookmark.domain.QCollectionBookmark.collectionBookmark;
import static kr.flint.content.domain.QContent.content;


@Repository
@RequiredArgsConstructor
public class CollectionQueryRepository {
	private final JPAQueryFactory jpaQueryFactory;

	public List<GetCollectionSimpleRes> getCollectionSimpleList(Long cursor, int size){
		return jpaQueryFactory
			.select(Projections.constructor(
				GetCollectionSimpleRes.class,
				collection.id,
				collection.image,
				collection.title,
				collection.description,
				Expressions.nullExpression(LocalDate.class)
			))
			.from(collection)
			.where(
				cursor != null ? collection.id.lt(cursor) : null,
				collection.isPublic.isTrue()
			)
			.orderBy(collection.id.desc())
			.limit(size + 1L)
			.fetch();
	}

	//Collection 상세조회 중 상단 부분
	public GetCollectionHeader getHeader(Long collectionId, Long userId){
		return jpaQueryFactory
			.select(Projections.constructor(
				GetCollectionHeader.class,
				collection.id,
				collection.title,
				collection.description,
				collection.image,
				collection.createdAt,

				user.id,
				user.nickname,
				user.profileImage,
				user.userRole.stringValue(),

				collectionBookmark.id.isNotNull()
			))
			.from(collection)
			.join(user).on(user.id.eq(collection.userId))
			.leftJoin(collectionBookmark).on(collectionBookmark.collectionId.eq(collection.id)
				.and(collectionBookmark.userId.eq(userId)))
			.where(collection.id.eq(collectionId))
			.fetchOne();
	}

	public List<GetCollectionDetailRes.Content> getContentList(Long collectionId, Long userId){
		return jpaQueryFactory
			.select(Projections.constructor(
				GetCollectionDetailRes.Content.class,
				content.id,
				content.title,
				content.poster,
				content.author,

				contentBookmark.id.isNotNull(),
				content.bookmarkCount,

				collectionContent.isSpoiler,
				collectionContent.reason
			))
			.from(collectionContent)
			.join(content).on(content.id.eq(collectionContent.contentId))
			.leftJoin(contentBookmark).on(
				contentBookmark.contentId.eq(content.id)
					.and(contentBookmark.userId.eq(userId))
			)
			.where(collectionContent.collection.id.eq(collectionId))
			.fetch();
	}

	public List<GetCollectionDetailListRes> getCollectionDetailList(Long userId){
		return jpaQueryFactory
			.select(Projections.constructor(
				GetCollectionDetailListRes.class,
				collection.id,
				collection.title,
				collection.description,
				collection.image,
				collection.bookmarkCount,

				collectionBookmark.id.isNotNull(),

				user.id,
				user.nickname,
				user.profileImage
			))
			.from(recentViewedCollection)
			.join(collection).on(collection.id.eq(recentViewedCollection.collection.id))
			.join(user).on(user.id.eq(collection.userId))
			.leftJoin(collectionBookmark).on(
				collectionBookmark.collectionId.eq(collection.id)
					.and(collectionBookmark.userId.eq(userId))
			)
			.where(recentViewedCollection.userId.eq(userId))
			.orderBy(recentViewedCollection.createdAt.desc())
			.fetch();
	}

	public record GetCollectionHeader(
		Long collectionId,
		String title,
		String description,
		String imageUrl,
		LocalDateTime createdAt,

		Long authorId,
		String authorName,
		String authorProfileUrl,
		String userRole,

		boolean isBookmarked
	){
		public GetCollectionDetailRes.Author toAuthor(){
			return new GetCollectionDetailRes.Author(authorId, authorName, authorProfileUrl, userRole);
		}
	}
}
