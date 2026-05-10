package kr.flint.api.domain.search.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

public record BookmarkedCollectionSearchRes(
	@Schema(type = "string")
	Long bookmarkId,
	@Schema(type = "string")
	Long collectionId,
	@Schema(description = "컬렉션 대표 이미지 URL")
	String imageUrl,
	@Schema(description = "컬렉션 제목")
	String title,
	@Schema(description = "컬렉션 설명")
	String description,
	@Schema(description = "컬렉션 저장 수")
	Integer bookmarkCount,
	@Schema(description = "현재 사용자의 저장 여부 — 북마크한 컬렉션 검색이라 항상 true")
	Boolean isBookmarked,
	@Schema(type = "string", description = "작성자 ID")
	Long userId,
	@Schema(description = "작성자 닉네임")
	String nickname,
	@Schema(description = "작성자 프로필 이미지 URL")
	String profileImageUrl,
	@ArraySchema(schema = @Schema(implementation = String.class, description = "컬렉션에 포함된 작품 포스터 URL 목록"))
	List<String> contentImages
) {
	// QueryDSL Projections.constructor 용 — contentImages는 facade에서 batch 조회 후 채우고, isBookmarked는 항상 true로 고정.
	public BookmarkedCollectionSearchRes(
		Long bookmarkId,
		Long collectionId,
		String imageUrl,
		String title,
		String description,
		Integer bookmarkCount,
		Long userId,
		String nickname,
		String profileImageUrl
	) {
		this(bookmarkId, collectionId, imageUrl, title, description,
			bookmarkCount, true, userId, nickname, profileImageUrl, List.of());
	}
}
