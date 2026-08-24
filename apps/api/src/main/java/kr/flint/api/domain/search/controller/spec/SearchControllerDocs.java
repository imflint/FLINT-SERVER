package kr.flint.api.domain.search.controller.spec;

import org.springframework.http.ResponseEntity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.flint.api.domain.search.dto.response.GetContentSearchListRes;
import kr.flint.api.domain.search.dto.response.BookmarkedCollectionSearchRes;
import kr.flint.api.domain.search.dto.response.BookmarkedContentSearchRes;
import kr.flint.shared.dto.PaginationResponse;
import kr.flint.shared.dto.response.SuccessResponse;

@Tag(name = "Search", description = "검색 API")
public interface SearchControllerDocs {

	@Operation(
		summary = "콘텐츠 검색 - 재민",
		description = "키워드로 콘텐츠를 검색합니다. 검색어가 없으면 인기 콘텐츠를 최대 30개 반환합니다."
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "검색 성공", useReturnTypeSchema = true)
	})
	@Deprecated
	ResponseEntity<SuccessResponse<GetContentSearchListRes>> searchContent(
		@Parameter(description = "검색 키워드", example = "주토피아")
		String keyword
	);

	@Operation(
		summary = "북마크한 컬렉션 검색 - 호주",
		description = "사용자가 북마크한 컬렉션 중에서 키워드로 검색합니다. 제목과 설명에서 검색합니다."
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "검색 성공", useReturnTypeSchema = true)
	})
	ResponseEntity<SuccessResponse<PaginationResponse<BookmarkedCollectionSearchRes>>> searchBookmarkedCollections(
		Long userId,
		@Parameter(description = "검색 키워드", example = "넷플릭스", required = true)
		String keyword,
		@Parameter(description = "다음 페이지 커서", example = "12345")
		Long cursor,
		@Parameter(description = "페이지 크기", example = "20")
		int size
	);

	@Operation(
		summary = "북마크한 작품 검색 - 호주",
		description = "사용자가 북마크한 작품 중에서 키워드로 검색합니다. 제목과 감독/작가에서 검색합니다."
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "검색 성공", useReturnTypeSchema = true)
	})
	ResponseEntity<SuccessResponse<PaginationResponse<BookmarkedContentSearchRes>>> searchBookmarkedContents(
		Long userId,
		@Parameter(description = "검색 키워드", example = "눈물의 여왕", required = true)
		String keyword,
		@Parameter(description = "다음 페이지 커서", example = "12345")
		Long cursor,
		@Parameter(description = "페이지 크기", example = "20")
		int size
	);
}
