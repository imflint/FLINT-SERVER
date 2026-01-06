package kr.flint.collection.dto.request;

import jakarta.validation.constraints.NotNull;

public record AddContentReq(
	@NotNull(message = "작품 Id는 필수 입력값입니다")
	Long contentId,
	@NotNull(message = "스포일러 여부는 필수 입력값입니다")
	boolean isSpoiler,
	@NotNull(message = "작품 선정 이뉴는 필수 입ㄹ력 값입니다")
	String reason
) {
}
