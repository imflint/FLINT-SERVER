package kr.flint.api.domain.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "북마크한 콘텐츠 개수 응답")
public record GetBookmarkedContentCountRes(
	@Schema(description = "사용자가 북마크한 전체 콘텐츠 수", example = "12")
	int totalCount
) {
	public static GetBookmarkedContentCountRes from(int totalCount) {
		return new GetBookmarkedContentCountRes(totalCount);
	}
}
