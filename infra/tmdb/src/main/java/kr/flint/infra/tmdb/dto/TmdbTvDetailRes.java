package kr.flint.infra.tmdb.dto;

import java.util.List;

public record TmdbTvDetailRes(
	List<Creator> created_by,
	List<TmdbGenre> genres
) {
	public record Creator(String name){}
	public record TmdbGenre(
			Long id,
			String name
		){}
}

