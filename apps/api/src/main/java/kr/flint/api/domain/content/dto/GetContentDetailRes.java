package kr.flint.api.domain.content.dto;

import java.util.List;

public record GetContentDetailRes(
	Long contentId,
	String title,
	int year,
	List<GetOttSimpleRes> getOttSimpleList
) {
	public record GetOttSimpleRes(
		Long ottId,
		String logoUrl
	){}
}
