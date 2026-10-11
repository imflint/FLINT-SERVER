package kr.flint.api.domain.content.controller.spec;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import kr.flint.api.domain.content.dto.GetBookmarkedContentCountRes;
import kr.flint.api.domain.content.dto.GetContentDetailRes;
import kr.flint.api.domain.content.dto.GetOttListRes;
import kr.flint.api.domain.content.dto.SearchGenre;
import kr.flint.content.domain.MediaType;
import kr.flint.shared.dto.PaginationResponse;
import kr.flint.shared.dto.response.SuccessResponse;
import kr.flint.shared.exception.ProblemDetail;

@Tag(name = "Content", description = "콘텐츠 API")
public interface ContentControllerDocs {

	@Operation(
		summary = "콘텐츠별 OTT 목록 조회 - 재민",
		description = "특정 콘텐츠를 시청할 수 있는 OTT 플랫폼 목록을 조회합니다. 사용자가 구독 중인 OTT가 우선 표시됩니다."
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공", useReturnTypeSchema = true),
		@ApiResponse(
			responseCode = "404",
			description = "존재하지 않는 콘텐츠",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class))
		)
	})
	ResponseEntity<SuccessResponse<GetOttListRes>> getOttList(
		Long userId,
		@Parameter(description = "콘텐츠 ID", example = "1")
		Long contentId
	);

	@Operation(
		summary = "북마크한 콘텐츠 목록 조회 - 재민",
		description = "현재 로그인한 사용자가 북마크한 콘텐츠 목록을 북마크 최신순으로 cursor 페이지네이션 조회합니다. 영화 감독 또는 TV creator를 nullable author로 반환합니다."
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공", useReturnTypeSchema = true),
		@ApiResponse(
			responseCode = "400",
			description = "size가 허용 범위를 벗어난 경우",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class))
		)
	})
	ResponseEntity<SuccessResponse<PaginationResponse<GetContentDetailRes>>> getBookmarkContent(
		@Parameter(hidden = true)
		Long userId,
		@Parameter(description = "이전 응답의 nextCursor. 첫 페이지는 미입력", example = "801473411402740986")
		Long cursor,
		@Parameter(description = "페이지당 결과 수 (1~50, 기본 10)", example = "10")
		@Min(value = 1, message = "size는 1 이상이어야 합니다.")
		int size
	);

	@Operation(
		summary = "북마크한 콘텐츠 개수 조회",
		description = "현재 로그인한 사용자가 북마크한 콘텐츠 전체 개수를 조회합니다."
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공", useReturnTypeSchema = true)
	})
	ResponseEntity<SuccessResponse<GetBookmarkedContentCountRes>> getBookmarkedContentCount(
		@Parameter(hidden = true)
		Long userId
	);

	@Operation(
		summary = "콘텐츠 검색 - 재민",
		description = """
			- 로컬 DB에 저장된 콘텐츠를 커서 페이지네이션으로 반환합니다.
			- `keyword`는 NFKC·소문자·문자와 숫자 외 제거 후 유니코드 코드포인트 1자 이상이어야 합니다. 한 글자 포함 검색 활성화 시 1자는 정규화 제목의 LIKE 포함 조건으로 조회하며 전체 일치 우선·ID 내림차순입니다. 한 글자 커서는 v5이며 FULLTEXT를 사용하지 않습니다. 비활성화 시 기존 전체 일치만 지원합니다. 2자 이상은 기존 검색을 유지하고 실제 영문 단어 매칭에서는 단어 사이 공백을 보존합니다. 미입력 또는 공백만 입력하면 검색어 필터를 적용하지 않습니다.
			- 2자 이상 검색어는 완전 일치, FULLTEXT 관련도, 콘텐츠 ID 내림차순으로 정렬합니다. 검색어가 없으면 인기순(북마크 수, 콘텐츠 ID 내림차순)으로 정렬합니다.
			- 영문 단어 검색이 활성화되면 영문 ASCII 문자·숫자 검색어는 전체 단어가 입력 순서대로 인접해야 합니다. 단어 사이 공백은 보존하며 연속 공백은 통합합니다. 접두어·단어 중간·임의 띄어쓰기 검색은 지원하지 않습니다. 한글·혼합·숫자만 입력한 검색은 기존 방식을 유지합니다.
			- `genre`는 `?genre=ACTION`처럼 단일 장르를 지정합니다. 기존 반복 파라미터나 쉼표 입력은 첫 값만 적용하며 이후 값은 검증하지 않습니다.
			- `genre` 미입력 또는 첫 값이 비어 있으면 장르 필터를 적용하지 않습니다. 첫 값이 유효하지 않으면 400을 반환합니다.
			- `mediaType` 미입력 시 MOVIE/TV 전체를 대상으로 검색합니다.
			- 조건이 없으면 로컬 DB 콘텐츠를 인기순으로 반환합니다.
			- `cursor`는 이전 응답의 `meta.nextCursor`를 그대로 전달하는 cursor token입니다.
			- localized 검색은 keyword v2, 검색 read model은 keyword v3 커서를 사용합니다. 현재 모드와 다른 keyword 커서는 400을 반환하므로 첫 페이지부터 다시 조회해야 합니다. 인기순 커서는 v1을 유지합니다.
			- 영문 단어 검색 활성화 시 keyword v4 커서를 사용하며 한글 검색 v3·인기순 v1은 유지합니다. 이전 영문 keyword 커서는 400을 반환합니다.
			"""
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "검색 성공", useReturnTypeSchema = true),
		@ApiResponse(
			responseCode = "400",
			description = "keyword에 정규화 후 문자와 숫자가 없거나 genre 또는 cursor가 유효하지 않거나 size가 허용 범위를 벗어난 경우",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class))
		)
	})
	ResponseEntity<?> searchContent(
		@Parameter(description = "검색 키워드. 정규화 후 1자는 포함 검색 활성화 시 정규화 제목 LIKE 검색, 비활성화 시 전체 일치만. 2자 이상은 기존 검색. 미입력 또는 공백만 입력하면 필터 없음", example = "눈물의 여왕")
		String keyword,
		@Parameter(description = "단일 장르 필터. 복수 입력은 첫 값만 적용하며 검색어·미디어 타입과 AND 조건으로 검색합니다.", example = "ACTION")
		SearchGenre genre,
		@Parameter(description = "미디어 타입 필터. 미입력 시 전체 타입을 검색합니다.", example = "MOVIE")
		MediaType mediaType,
		@Parameter(description = "이전 응답의 nextCursor token. 첫 페이지는 미입력", example = "MzoxMjM0NTY3ODkw")
		String cursor,
		@Parameter(description = "페이지당 결과 수 (1 이상, 1 미만이면 400)", example = "20")
		@Min(value = 1, message = "size는 1 이상이어야 합니다.")
		int size
	);

}
