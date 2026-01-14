package kr.flint.api.domain.content.controller.spec;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.flint.api.domain.content.dto.GetContentSearchRes;
import kr.flint.shared.dto.PaginationResponse;

@Schema(description = "콘텐츠 검색 성공 응답")
public record SearchContentSuccessResponse(

	@Schema(example = "SUCCESS_FETCH")
	String code,

	@Schema(example = "조회 성공")
	String message,

	PaginationResponse<GetContentSearchRes> data
) {}
