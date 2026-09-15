package kr.flint.infra.tmdb.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TmdbTvFullDetailRes(
	Long id,
	String name,
	@JsonProperty("original_name") String originalName,
	@JsonProperty("original_language") String originalLanguage,
	String overview,
	@JsonProperty("poster_path") String posterPath,
	@JsonProperty("first_air_date") String firstAirDate,
	List<TmdbTvDetailRes.Creator> created_by,
	List<TmdbTvDetailRes.TmdbGenre> genres,
	Boolean adult,
	@JsonProperty("vote_average") Double voteAverage,
	TmdbTranslationsRes translations,
	TmdbMovieCreditRes credits,
	@JsonProperty("watch/providers") TmdbOttRes watchProviders
) {
}
