package kr.flint.api.domain.search.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record BookmarkedContentSearchRes(
	@Schema(type = "string")
	Long bookmarkId,
	@Schema(type = "string")
	Long contentId,
	String title,
	String author,
	String posterUrl,
	int year
) {
}
