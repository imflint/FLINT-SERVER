package kr.flint.api.domain.collection.dto.response;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonInclude;

import kr.flint.collection.domain.Collection;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GetCollectionSimpleRes(
	Long collectionId,
	String imageUrl,
	String title,
	String description,
	LocalDate createdAt
) {
	public static GetCollectionSimpleRes of(Collection collection) {
		return new GetCollectionSimpleRes(
			collection.getId(),
			collection.getImage(),
			collection.getTitle(),
			collection.getDescription(),
			null
		);
	}

	public static GetCollectionSimpleRes ofWithCreatedAt(Collection collection) {
		return new GetCollectionSimpleRes(
			collection.getId(),
			collection.getImage(),
			collection.getTitle(),
			collection.getDescription(),
			collection.getCreatedAt().toLocalDate()
		);
	}
}
