package kr.flint.infra.tmdb.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TmdbChangesRes(
	List<Result> results,
	int page,
	@JsonProperty("total_pages") int totalPages,
	@JsonProperty("total_results") int totalResults
) {
	public record Result(Long id, Boolean adult) {
	}
}
