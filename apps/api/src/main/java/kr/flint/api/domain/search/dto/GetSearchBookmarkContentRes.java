package kr.flint.api.domain.search.dto;

import java.util.List;

public record GetSearchBookmarkContentRes(
	Long id,
	String title,
	String author,
	String posterUrl,
	int year,
	List<GetSearchBookmarkContentRes.GetOttSimpleRes> getOttSimpleList,
	int bookmarkCount
) {
	public record GetOttSimpleRes(
		Long ottId,
		String logoUrl
	) {
	}
}
