package kr.flint.infra.tmdb.dto;

import java.util.List;

public record TmdbMovieCreditRes(
	List<Crew> crew
) {
	public record Crew(String job, String name){}
}
