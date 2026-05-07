package kr.flint.api.domain.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record CollectionContentImageDto(
	@Schema(type = "string")
	Long collectionId,
	String customImage,
	String poster
) {
	public String image() {
		return customImage != null && !customImage.isBlank() ? customImage : poster;
	}
}
