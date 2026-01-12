package kr.flint.api.domain.content.dto;

import java.util.List;

import kr.flint.ott.dto.GetOttSimpleRes;

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
