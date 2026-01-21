package kr.flint.api.domain.collection.dto.response;

import java.time.LocalDate;
import java.util.function.Function;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.flint.collection.domain.Collection;

public record GetCollectionSimpleRes(
	@Schema(type = "string")
	Long collectionId,
	String imageUrl,
	String title,
	String description
) {
	public static GetCollectionSimpleRes of(Collection collection, Function<String, String> imageUrlResolver) {
		return new GetCollectionSimpleRes(
			collection.getId(),
			imageUrlResolver.apply(collection.getImage()),
			collection.getTitle(),
			collection.getDescription()
		);
	}

	public static GetCollectionSimpleRes ofWithCreatedAt(Collection collection, Function<String, String> imageUrlResolver) {
		return new GetCollectionSimpleRes(
			collection.getId(),
			imageUrlResolver.apply(collection.getImage()),
			collection.getTitle(),
			collection.getDescription()
		);
	}

	public GetCollectionSimpleRes withResolvedImageUrl(Function<String, String> imageUrlResolver) {
		return new GetCollectionSimpleRes(
			collectionId,
			imageUrlResolver.apply(imageUrl),
			title,
			description
		);
	}
}
