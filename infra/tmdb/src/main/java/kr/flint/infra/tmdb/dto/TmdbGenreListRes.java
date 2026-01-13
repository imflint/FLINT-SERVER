package kr.flint.infra.tmdb.dto;

import java.util.List;

public record TmdbGenreListRes(
	List<TmdbGenre> genres
) {
	public record TmdbGenre(
		Long id,
		String name
	){}
}
