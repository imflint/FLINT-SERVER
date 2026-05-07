package kr.flint.api.domain.collection.repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;

import kr.flint.api.domain.collection.dto.response.GetCollectionDetailListRes;
import kr.flint.api.domain.collection.dto.response.GetCollectionDetailRes;
import kr.flint.api.domain.collection.dto.response.GetCollectionSimpleRes;
import lombok.RequiredArgsConstructor;

import static kr.flint.api.common.query.CollectionQueryConditions.*;
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

	// 탐색 - 컬렉션 목록 조회 (각 컬렉션에서 랜덤 작품의 정보 반환)
	public List<GetCollectionSimpleRes> getCollectionSimpleList(Long cursor, int size){
		// 컬렉션 ID 목록 조회 (description 10자 이상 + reason 10자 이상인 작품이 있는 컬렉션만)
		List<CollectionSimpleRow> collectionRows = jpaQueryFactory
			.select(Projections.constructor(
				CollectionSimpleRow.class,
				collection.id,
				collection.image
			))
			.from(collection)
			.where(
				cursor != null ? collection.id.lt(cursor) : null,
				collection.isPublic.isTrue(),
				hasValidDescription(),
				hasContentWithValidReason()
			)
			.orderBy(collection.createdAt.desc())
			.limit(size + 1L)
			.fetch();

		if (collectionRows.isEmpty()) {
			return List.of();
		}

		List<Long> collectionIds = collectionRows.stream()
			.map(CollectionSimpleRow::collectionId)
			.toList();

		// 해당 컬렉션들의 content 정보 조회 (reason 10자 이상인 작품만)
		List<CollectionContentInfoRow> contentRows = jpaQueryFactory
			.select(Projections.constructor(
				CollectionContentInfoRow.class,
				collectionContent.collection.id,
				content.title,
				collectionContent.reason
			))
			.from(collectionContent)
			.join(content).on(content.id.eq(collectionContent.contentId))
			.where(
				collectionContent.collection.id.in(collectionIds),
				collectionContent.reason.isNotNull(),
				collectionContent.reason.length().goe(REASON_MIN_LENGTH)
			)
			.fetch();

		// 컬렉션별로 그룹핑
		Map<Long, List<CollectionContentInfoRow>> contentMap = contentRows.stream()
			.collect(Collectors.groupingBy(CollectionContentInfoRow::collectionId));

		// 각 컬렉션마다 랜덤 작품 선택하여 DTO 생성
		return collectionRows.stream()
			.filter(row -> contentMap.containsKey(row.collectionId()))
			.map(row -> {
				List<CollectionContentInfoRow> contents = contentMap.get(row.collectionId());
				int randomIndex = ThreadLocalRandom.current().nextInt(contents.size());
				CollectionContentInfoRow selected = contents.get(randomIndex);
				return new GetCollectionSimpleRes(
					row.collectionId(),
					row.imageUrl(),
					selected.contentTitle(),
					selected.contentDescription()
				);
			})
			.toList();
	}

	public record CollectionSimpleRow(
		Long collectionId,
		String imageUrl
	) {}

	public record CollectionContentInfoRow(
		Long collectionId,
		String contentTitle,
		String contentDescription
	) {}

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
				collectionContent.customImage,
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
					collectionContent.customImage,
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
			String image = row.image();
			if (image == null) continue;

			List<String> list = imageMap.computeIfAbsent(row.collectionId(), k -> new ArrayList<>());
			if (list.size() < 2) {
				list.add(image);
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
		String customImage,
		String poster
	) {
		public String image() {
			return customImage != null && !customImage.isBlank() ? customImage : poster;
		}
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
