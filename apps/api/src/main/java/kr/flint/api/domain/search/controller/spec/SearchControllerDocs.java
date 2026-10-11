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
		description = "정규화 후 1자는 포함 검색 활성화 시 정규화 제목 LIKE 검색, 전체 일치 우선·ID 내림차순으로 최대 50개 반환합니다. 비활성화 시 전체 일치만 지원합니다. 2자 이상은 기존 정확 일치·관련도·ID 순과 무제한 배열입니다. 검색어가 없으면 인기 콘텐츠를 최대 30개 반환합니다. 2자 이상 영문 단어 검색 활성화 시 전체 단어의 연속 구문만 일치하며 접두어·중간 문자열·임의 띄어쓰기는 지원하지 않습니다. 2자 이상 한글 검색은 기존 search_title을 사용합니다."
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "검색 성공", useReturnTypeSchema = true),
		@ApiResponse(responseCode = "400", description = "정규화 후 검색어에 문자와 숫자가 없는 경우")
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
		description = "사용자가 저장한 작품의 제목을 검색합니다. 정규화 후 1자는 포함 검색 활성화 시 정규화 제목 LIKE 검색, 비활성화 시 전체 일치만 지원합니다. 2자 이상은 기존 검색을 적용합니다. 2자 이상 영문 단어 검색 활성화 시 전체 단어가 입력 순서대로 인접한 구문만 일치합니다. 접두어·중간 문자열·임의 띄어쓰기는 지원하지 않습니다. 2자 이상 한글 검색은 기존 search_title을 사용합니다. 최신 저장순과 북마크 ID 커서를 유지합니다. 감독/작가는 검색 대상이 아닙니다."
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "검색 성공", useReturnTypeSchema = true),
		@ApiResponse(responseCode = "400", description = "정규화 후 검색어에 문자와 숫자가 없는 경우")
	})
	ResponseEntity<SuccessResponse<PaginationResponse<BookmarkedContentSearchRes>>> searchBookmarkedContents(
		Long userId,
		@Parameter(description = "정규화 후 1자는 포함 검색 활성화 시 정규화 제목 LIKE 검색, 비활성화 시 전체 일치만. 2자 이상은 기존 검색. 미입력·공백은 저장 목록 조회", example = "눈물의 여왕")
		String keyword,
		@Parameter(description = "다음 페이지 커서", example = "12345")
		Long cursor,
		@Parameter(description = "페이지 크기", example = "20")
		int size
	);
}
