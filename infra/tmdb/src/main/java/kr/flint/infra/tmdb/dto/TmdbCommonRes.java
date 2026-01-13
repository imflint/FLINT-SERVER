package kr.flint.infra.tmdb.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TmdbCommonRes(
	int page,

	@JsonProperty("results")
	List<TmdbContentRes> contentList,

	@JsonProperty("total_pages")
	int totalPage,

	@JsonProperty("total_results")
	int totalResult
) {
}
