package kr.flint.infra.tmdb.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TmdbMovieDetailRes(
	Long id,
	String title,
	@JsonProperty("original_title") String originalTitle,
	String overview,
	@JsonProperty("poster_path") String posterPath,
	@JsonProperty("release_date") String releaseDate,
	List<TmdbGenreListRes.TmdbGenre> genres,
	Boolean adult,
	@JsonProperty("vote_average") Double voteAverage
) {
}
