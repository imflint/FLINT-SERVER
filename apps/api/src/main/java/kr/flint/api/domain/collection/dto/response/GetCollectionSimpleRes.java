package kr.flint.api.domain.collection.dto.response;

import java.time.LocalDate;
import java.util.function.Function;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import kr.flint.collection.domain.Collection;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GetCollectionSimpleRes(
	@JsonSerialize(using = ToStringSerializer.class)
	Long collectionId,
	String imageUrl,
	String title,
	String description,
	LocalDate createdAt
) {
	public static GetCollectionSimpleRes of(Collection collection, Function<String, String> imageUrlResolver) {
		return new GetCollectionSimpleRes(
			collection.getId(),
			imageUrlResolver.apply(collection.getImage()),
			collection.getTitle(),
			collection.getDescription(),
			null
		);
	}

	public static GetCollectionSimpleRes ofWithCreatedAt(Collection collection, Function<String, String> imageUrlResolver) {
		return new GetCollectionSimpleRes(
			collection.getId(),
			imageUrlResolver.apply(collection.getImage()),
			collection.getTitle(),
			collection.getDescription(),
			collection.getCreatedAt().toLocalDate()
		);
	}

	public GetCollectionSimpleRes withResolvedImageUrl(Function<String, String> imageUrlResolver) {
		return new GetCollectionSimpleRes(
			collectionId,
			imageUrlResolver.apply(imageUrl),
			title,
			description,
			createdAt
		);
	}
}
