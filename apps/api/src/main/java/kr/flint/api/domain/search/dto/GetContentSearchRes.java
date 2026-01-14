package kr.flint.api.domain.search.dto;

import kr.flint.content.domain.Content;

public record GetContentSearchRes(
	Long contentId, //tmdb id
	String title,
	String author,
	String posterUrl,
	int year
) {
	public static GetContentSearchRes of(Long id, String title, String author, String posterUrl, int year) {
		return new GetContentSearchRes(id, title, author, posterUrl, year);
	}

	public static GetContentSearchRes from(Content content) {
		return new GetContentSearchRes(
			content.getId(),
			content.getTitle(),
			content.getAuthor(),
			content.getPoster(),
			content.getYear()
		);
	}
}
