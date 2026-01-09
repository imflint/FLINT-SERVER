package kr.flint.api.collection.dto.response;

public record GetCollectionDetailListRes(
	Long collectionId,
	String title,
	String description,
	String imageUrl,
	Integer bookmarkCount,
	Boolean isBookmarked,

	Long userId,
	String nickname,
	String profileUrl
) {
}
