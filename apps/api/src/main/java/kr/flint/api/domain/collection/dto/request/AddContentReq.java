package kr.flint.api.domain.collection.dto.request;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import kr.flint.collection.dto.CollectionCreateCommand.ContentInput;

@Schema(description = "컬렉션에 추가할 작품 정보")
public record AddContentReq(
	@Schema(description = "작품 ID", example = "800388257884431200")
	@NotNull(message = "작품 Id는 필수 입력값입니다")
	Long contentId,

	@Schema(description = "스포일러 포함 여부", example = "false")
	@NotNull(message = "스포일러 여부는 필수 입력값입니다")
	Boolean isSpoiler,

	@Schema(description = "작품 선정 이유", example = "감동적인 스토리와 연출이 인상적이에요")
	@NotBlank(message = "작품 선정 이유는 필수 입력 값입니다")
	String reason,

	@Schema(description = "작품별 커스텀 이미지 S3 key 목록 (선택)", example = "[\"collection/content/260505/abc123.jpg\"]")
	@Nullable
	List<String> customImages
) {

	public ContentInput toInput() {
		return ContentInput.of(contentId, isSpoiler, reason, customImages);
	}
}
