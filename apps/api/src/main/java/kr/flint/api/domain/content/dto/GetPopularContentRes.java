package kr.flint.api.domain.content.dto;

import java.util.List;

public record GetPopularContentRes(
	List<GetContentSearchRes> contentSearchList,
	int page
) {
	public static GetPopularContentRes from(List<GetContentSearchRes> contentSearchRes, int page) {
		return new GetPopularContentRes(contentSearchRes, page);
	}
}
