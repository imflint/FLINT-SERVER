package kr.flint.api.domain.collection.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "컬렉션 상세 응답")
public record GetCollectionDetailRes(
	@Schema(description = "컬렉션 ID", example = "1", type = "string")
	Long id,
	@Schema(description = "컬렉션 제목", example = "주말에 보기 좋은 영화")
	String title,
	@Schema(description = "컬렉션 설명", example = "편하게 볼 수 있는 영화들을 모았습니다")
	String description,
	@Schema(description = "컬렉션 이미지 URL", example = "https://example.com/image.jpg")
	String thumbnailUrl,
	@Schema(description = "생성일", example = "2024-01-15")
	LocalDate createdAt,
	@Schema(description = "북마크 여부", example = "true")
	boolean isBookmarked,

	@Schema(description = "작성자 정보")
	Author author,

	@ArraySchema(schema = @Schema(implementation = Content.class))
	List<Content> contents
) {
	@Schema(description = "컬렉션 작성자")
	public record Author(
		@Schema(description = "사용자 ID", example = "123", type = "string")
		Long id,
		@Schema(description = "닉네임", example = "플린트")
		String nickname,
		@Schema(description = "프로필 이미지 URL", example = "https://example.com/profile.jpg")
		String profileImageUrl,
		@Schema(description = "사용자 역할 (ADMIN, FLINER, FLING)", example = "FLINER")
		String userRole
	){}

	@Schema(description = "컬렉션 내 콘텐츠")
	public record Content(
		@Schema(description = "콘텐츠 ID", example = "456", type = "string")
		Long id,
		@Schema(description = "콘텐츠 제목", example = "눈물의 여왕")
		String title,
		@Schema(description = "썸네일 URL", example = "https://example.com/thumb.jpg")
		String imageUrl,
		@Schema(description = "작품별 커스텀 이미지 URL (선택)", example = "https://example.com/custom.jpg")
		String customImageUrl,
		@Schema(description = "감독/작가", example = "아자스")
		String director,
		@Schema(description = "북마크 여부", example = "false")
		boolean isBookmarked,
		@Schema(description = "북마크 수", example = "120")
		int bookmarkCount,
		@Schema(description = "스포일러 포함 여부", example = "false")
		boolean isSpoiler,
		@Schema(description = "선정 이유", example = "감동적인 스토리")
		String reason,
		@Schema(description = "개봉일", example = "2026")
		int year
		){
		public Content resolveImages(Function<String, String> imageUrlResolver) {
			return new Content(
				id,
				title,
				imageUrlResolver.apply(imageUrl),
				imageUrlResolver.apply(customImageUrl),
				director,
				isBookmarked,
				bookmarkCount,
				isSpoiler,
				reason,
				year
			);
		}
	}
}
