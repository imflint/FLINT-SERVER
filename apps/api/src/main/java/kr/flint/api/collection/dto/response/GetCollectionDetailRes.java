package kr.flint.api.collection.dto.response;

import java.time.LocalDate;
import java.util.List;

public record GetCollectionDetailRes(
	Long collectionId,
	String title,
	String description,
	String imageUrl,
	LocalDate createdAt,
	boolean isBookmarked,

	Author author,

	List<Content> contentList
) {
	public record Author(
		Long userId,
		String nickname,
		String profileUrl,
		String userRole
	){}

	public record Content(
		Long contentId,
		String title,
		String thumbnailUrl,
		String authorName,
		boolean isBookmarked,
		int bookmarkCount,
		boolean isSpoiler,
		String reason
	){}
}
