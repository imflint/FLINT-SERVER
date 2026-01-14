package kr.flint.api.domain.search.controller.spec;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.flint.api.domain.search.dto.response.BookmarkedCollectionSearchRes;
import kr.flint.shared.dto.PaginationResponse;

@Schema(description = "북마크 컬렉션 검색 성공 응답")
public record SearchBookmarkedCollectionSwaggerResponse(
	@Schema(example = "SUCCESS_FETCH")
	String code,

	@Schema(example = "조회 성공")
	String message,

	PaginationResponse<BookmarkedCollectionSearchRes> data
) {
}
