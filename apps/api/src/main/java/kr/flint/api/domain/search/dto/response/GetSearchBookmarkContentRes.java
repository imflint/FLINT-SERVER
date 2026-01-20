package kr.flint.api.domain.search.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public record GetSearchBookmarkContentRes(
	@Schema(type = "string")
	Long id,
	String title,
	String author,
	String posterUrl,
	int year,
	List<GetSearchBookmarkContentRes.GetOttSimpleRes> getOttSimpleList,
	int bookmarkCount
) {
	public record GetOttSimpleRes(
		@Schema(type = "string")
		Long ottId,
		String logoUrl
	) {
	}
}
