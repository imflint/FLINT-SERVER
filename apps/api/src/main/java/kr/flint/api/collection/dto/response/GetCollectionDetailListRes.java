package kr.flint.api.collection.dto.response;

public record GetCollectionDetailListRes(
	Long collectionId,
	String title,
	String description,
	int bookmarkCount,
	boolean isBookmarked,


	Long userId,
	String nickname,
	String profileUrl
) {
}
