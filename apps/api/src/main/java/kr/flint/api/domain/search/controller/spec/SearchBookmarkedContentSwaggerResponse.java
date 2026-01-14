package kr.flint.api.domain.search.controller.spec;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.flint.api.domain.search.dto.response.BookmarkedContentSearchRes;
import kr.flint.shared.dto.PaginationResponse;

@Schema(description = "북마크 작품 검색 성공 응답")
public record SearchBookmarkedContentSwaggerResponse(
	@Schema(example = "SUCCESS_FETCH")
	String code,

	@Schema(example = "조회 성공")
	String message,

	PaginationResponse<BookmarkedContentSearchRes> data
) {
}
