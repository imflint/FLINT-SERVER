package kr.flint.collection.dto.response;

import kr.flint.collection.domain.Collection;

public record GetCollectionSimpleRes(
	Long collectionId,
	String imageUrl,
	String title,
	String description
) {
	public static GetCollectionSimpleRes of(Collection collection) {
		return new GetCollectionSimpleRes(
			collection.getId(),
			collection.getImage(),
			collection.getTitle(),
			collection.getDescription()
		);
	}
}
