package kr.flint.api.domain.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "검색 장르 필터")
public enum SearchGenre {
	ACTION("액션"),
	ROMANCE("로맨스"),
	SCIENCE_FICTION("SF"),
	DRAMA("드라마"),
	COMEDY("코미디"),
	HORROR("공포");

	private final String genreName;

	SearchGenre(String genreName) {
		this.genreName = genreName;
	}

	public String genreName() {
		return genreName;
	}
}
