package kr.flint.infra.tmdb.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TmdbContentRes(
	Long id,

	@JsonProperty("genre_ids")
	List<Long> genreIdList,

	@JsonProperty("title")
	String title,

	@JsonProperty("name")
	String name,

	@JsonProperty("overview")
	String overview,

	@JsonProperty("poster_path")
	String poster,

	@JsonProperty("release_date")
	String releaseDate,

	@JsonProperty("first_air_date")
	String firstAirDate,

	@JsonProperty("media_type")
	String mediaType
) {
	// 영화는 title, TV는 name 필드 사용
	public String resolvedTitle() {
		return title != null ? title : name;
	}
}
