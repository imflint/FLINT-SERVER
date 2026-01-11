package kr.flint.api.domain.collection.dto.request;

import jakarta.validation.constraints.NotNull;
import kr.flint.collection.dto.CollectionCreateCommand.ContentInput;

public record AddContentReq(
	@NotNull(message = "작품 Id는 필수 입력값입니다")
	Long contentId,
	@NotNull(message = "스포일러 여부는 필수 입력값입니다")
	boolean isSpoiler,
	@NotNull(message = "작품 선정 이유는 필수 입력 값입니다")
	String reason
) {

	public ContentInput toInput() {
		return ContentInput.of(contentId, isSpoiler, reason);
	}
}
