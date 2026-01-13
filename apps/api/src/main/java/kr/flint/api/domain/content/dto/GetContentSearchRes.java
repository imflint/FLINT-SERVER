package kr.flint.api.domain.content.dto;

public record GetContentSearchRes(
	Long id, //tmdb id
	String title,
	String author,
	String posterUrl,
	int year
) {
	public static GetContentSearchRes of(Long id, String title, String author, String posterUrl, int year) {
		return new GetContentSearchRes(id, title, author, posterUrl, year);
	}
}
