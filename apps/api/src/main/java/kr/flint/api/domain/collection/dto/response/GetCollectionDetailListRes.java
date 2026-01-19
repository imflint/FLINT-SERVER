package kr.flint.api.domain.collection.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "최근 본 컬렉션 상세 응답")
public record GetCollectionDetailListRes(
	@Schema(description = "컬렉션 ID", example = "1")
	Long collectionId,
	@Schema(description = "컬렉션 제목", example = "주말에 보기 좋은 영화")
	String title,
	@Schema(description = "컬렉션 설명", example = "편하게 볼 수 있는 영화들")
	String description,
	@ArraySchema(schema = @Schema(implementation = String.class, example = "https://example.com/image.jpg"))
	List<String> imageList,
	@Schema(description = "북마크 수", example = "15")
	Integer bookmarkCount,
	@Schema(description = "북마크 여부", example = "true")
	Boolean isBookmarked,

	@Schema(description = "작성자 ID", example = "123")
	Long userId,
	@Schema(description = "작성자 닉네임", example = "플린트")
	String nickname,
	@Schema(description = "작성자 프로필 URL", example = "https://example.com/profile.jpg")
	String profileUrl
) {

}
