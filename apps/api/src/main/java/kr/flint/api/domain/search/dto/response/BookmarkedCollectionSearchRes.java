package kr.flint.api.domain.search.dto.response;

public record BookmarkedCollectionSearchRes(
	Long bookmarkId,
	Long collectionId,
	String imageUrl,
	String title,
	String description
) {
}
