package kr.flint.api.domain.search.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "컬렉션 검색 응답")
public record GetContentSearchListRes(
	@ArraySchema(schema = @Schema(implementation = GetContentSearchRes.class))
	List<GetContentSearchRes> contents
) {
}
