package kr.flint.api.domain.collection.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record CreateCollectionRes(
	@Schema(description = "컬렉션 ID", example = "1")
	Long collectionId
) {
}
