package kr.flint.api.domain.search.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record BookmarkedCollectionSearchRes(
	@Schema(type = "string")
	Long bookmarkId,
	@Schema(type = "string")
	Long collectionId,
	String imageUrl,
	String title,
	String description
) {
}
