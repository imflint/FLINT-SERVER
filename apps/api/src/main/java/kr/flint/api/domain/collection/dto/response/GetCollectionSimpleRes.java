package kr.flint.api.domain.collection.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record GetCollectionSimpleRes(
	@Schema(type = "string")
	Long collectionId,
	String imageUrl,
	String contentTitle,
	String contentDescription
) {}
