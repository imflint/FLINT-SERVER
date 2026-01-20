package kr.flint.api.domain.collection.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
			.orderBy(collection.createdAt.desc())
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
				collectionContent.reason,
				content.year
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

	public List<GetCollectionDetailListRes> getCollectionDetailList(Long userId) {

		// ✅ 1) baseRows: 컬렉션 중심으로만 가져오기 (썸네일은 collection.image)
		// - content / collectionContent 조인 제거 (조인 실패로 row 날아가는 것 방지)
		// - user는 leftJoin (soft delete/데이터 불일치 때문에 row 날아가는 것 방지)
		List<CollectionBaseRow> baseRows =
			jpaQueryFactory
				.select(Projections.constructor(
					CollectionBaseRow.class,
					collection.id,
					collection.image,              // ✅ collection_image 사용
					collection.title,
					collection.description,
					collection.bookmarkCount,
					collectionBookmark.id.isNotNull(),
					user.id,
					user.nickname,
					user.profileImage
				))
				.from(recentViewedCollection)
				.join(collection).on(collection.id.eq(recentViewedCollection.collection.id))
				.leftJoin(user).on(user.id.eq(collection.userId)) // ✅ left join
				.leftJoin(collectionBookmark).on(
					collectionBookmark.collectionId.eq(collection.id)
						.and(collectionBookmark.userId.eq(userId))
				)
				.where(recentViewedCollection.userId.eq(userId))
				.orderBy(recentViewedCollection.viewedAt.desc()) // ✅ 최근 본 기준
				.fetch();

		if (baseRows.isEmpty()) {
			return List.of();
		}

		List<Long> collectionIds = baseRows.stream()
			.map(CollectionBaseRow::collectionId)
			.distinct()
			.toList();

		// ✅ 2) imageRows: collection_content + content 조인으로 poster 가져오기
		//    각 collection 당 2개만 imageMap에 넣기
		List<ContentImageRow> imageRows =
			jpaQueryFactory
				.select(Projections.constructor(
					ContentImageRow.class,
					collectionContent.collection.id,
					content.poster
				))
				.from(collectionContent)
				.join(content).on(content.id.eq(collectionContent.contentId))
				.where(collectionContent.collection.id.in(collectionIds))
				.orderBy(
					collectionContent.collection.id.asc(),
					collectionContent.id.asc()
				)
				.fetch();

		Map<Long, List<String>> imageMap = new LinkedHashMap<>();
		for (ContentImageRow row : imageRows) {
			if (row.contentImage() == null) continue;

			List<String> list = imageMap.computeIfAbsent(row.collectionId(), k -> new ArrayList<>());
			if (list.size() < 2) {
				list.add(row.contentImage());
			}
		}

		// ✅ 3) DTO 매핑
		return baseRows.stream()
			.map(r -> new GetCollectionDetailListRes(
				r.collectionId(),
				r.thumbnailUrl(),
				r.title(),
				r.description(),
				imageMap.getOrDefault(r.collectionId(), List.of()),
				r.bookmarkCount(),
				r.isBookmarked(),
				r.authorId(),
				r.nickname(),
				r.profileUrl()
			))
			.toList();
	}

	public record CollectionBaseRow(
		Long collectionId,
		String thumbnailUrl,  // ✅ collection.image 들어옴
		String title,
		String description,
		Integer bookmarkCount,
		Boolean isBookmarked,
		Long authorId,
		String nickname,
		String profileUrl
	) {}

	public record ContentImageRow(
		Long collectionId,
		String contentImage
	) {}

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
