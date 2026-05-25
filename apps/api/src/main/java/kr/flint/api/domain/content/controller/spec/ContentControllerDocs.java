package kr.flint.api.domain.content.controller.spec;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
import kr.flint.api.domain.content.dto.GetContentListRes;
import kr.flint.api.domain.content.dto.GetOttListRes;
import kr.flint.api.domain.content.dto.SearchGenre;
import kr.flint.content.domain.MediaType;
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
		description = "현재 로그인한 사용자가 북마크한 콘텐츠 목록을 조회합니다."
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공", useReturnTypeSchema = true)
	})
	ResponseEntity<?> getBookmarkContent(Long userId);

	@Operation(
		summary = "콘텐츠 검색 - 재민",
		description = """
			- 로컬 DB에 저장된 콘텐츠를 인기순(북마크 많은 순)으로 페이지네이션해 반환합니다.
			- `keyword`는 콘텐츠 제목에 대해 검색합니다. 2자 이상 검색어는 FULLTEXT 인덱스를 사용하고, 1자 검색어는 기존 호환을 위해 부분 검색합니다.
			- `genre`는 `?genre=ACTION&genre=ROMANCE`처럼 반복 파라미터로 여러 개 지정할 수 있으며, 요청한 모든 장르를 가진 콘텐츠만 반환합니다.
			- `mediaType` 미입력 시 MOVIE/TV 전체를 대상으로 검색합니다.
			- 조건이 없으면 로컬 DB 콘텐츠를 인기순으로 반환합니다.
			- `cursor`는 1부터 시작하는 페이지 번호입니다.
			"""
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "검색 성공", useReturnTypeSchema = true),
		@ApiResponse(
			responseCode = "400",
			description = "cursor 또는 size가 허용 범위를 벗어난 경우",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class))
		)
	})
	ResponseEntity<?> searchContent(
		@Parameter(description = "검색 키워드", example = "눈물의 여왕")
		String keyword,
		@Parameter(description = "장르 필터. 반복 파라미터로 여러 개 지정 가능하며 AND 조건으로 검색합니다.", example = "ACTION")
		List<SearchGenre> genres,
		@Parameter(description = "미디어 타입 필터. 미입력 시 전체 타입을 검색합니다.", example = "MOVIE")
		MediaType mediaType,
		@Parameter(description = "페이지 번호 (1부터 시작, 1 미만이면 400)", example = "1")
		@Min(value = 1, message = "cursor는 1 이상이어야 합니다.")
		int cursor,
		@Parameter(description = "페이지당 결과 수 (1~50)", example = "20")
		@Min(value = 1, message = "size는 1 이상이어야 합니다.")
		@Max(value = 50, message = "size는 50 이하여야 합니다.")
		int size
	);

}
