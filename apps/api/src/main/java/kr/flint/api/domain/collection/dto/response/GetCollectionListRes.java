package kr.flint.api.domain.collection.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "최근 본 컬렉션 상세 응답")
public record GetCollectionListRes(
	@ArraySchema(schema = @Schema(implementation = GetCollectionDetailListRes.class))
	List<GetCollectionDetailListRes> collections
) {
}
