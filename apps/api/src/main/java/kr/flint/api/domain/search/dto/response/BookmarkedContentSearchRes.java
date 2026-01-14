package kr.flint.api.domain.search.dto.response;

public record BookmarkedContentSearchRes(
	Long bookmarkId,
	Long contentId,
	String title,
	String author,
	String posterUrl,
	int year
) {
}
