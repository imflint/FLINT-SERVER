package kr.flint.api.domain.search.controller.spec;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.flint.api.domain.search.dto.GetContentSearchRes;

@Schema(description = "콘텐츠 검색 성공 응답")
public record SearchContentSwaggerResponse(
	@Schema(example = "SUCCESS_FETCH")
	String code,

	@Schema(example = "조회 성공")
	String message,

	List<GetContentSearchRes> data
) {
}
