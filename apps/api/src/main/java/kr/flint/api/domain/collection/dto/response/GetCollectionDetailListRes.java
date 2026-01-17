package kr.flint.api.domain.collection.dto.response;

import java.util.List;

public record GetCollectionDetailListRes(
	Long collectionId,
	String title,
	String description,
	List<String> imageList,
	Integer bookmarkCount,
	Boolean isBookmarked,

	Long userId,
	String nickname,
	String profileUrl
) {

}
