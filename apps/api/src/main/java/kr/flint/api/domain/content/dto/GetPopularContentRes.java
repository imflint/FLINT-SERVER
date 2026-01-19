package kr.flint.api.domain.content.dto;

import java.util.List;

import kr.flint.api.domain.search.dto.response.GetContentSearchRes;

public record GetPopularContentRes(
	List<GetContentSearchRes> contents,
	int page
) {
	public static GetPopularContentRes from(List<GetContentSearchRes> contentSearchRes, int page) {
		return new GetPopularContentRes(contentSearchRes, page);
	}
}
