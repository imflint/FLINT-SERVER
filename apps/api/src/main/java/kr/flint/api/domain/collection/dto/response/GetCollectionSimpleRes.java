package kr.flint.api.domain.collection.dto.response;


public record GetCollectionSimpleRes(
	Long collectionId,
	String imageUrl,
	String contentTitle,
	String contentDescription
) {}
