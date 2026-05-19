package kr.flint.api.domain.content.controller.spec;

import java.util.List;

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
import kr.flint.ott.dto.GetOttResponse;
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
			- `genre` 지정 시: 로컬 DB에서 해당 장르 작품을 인기순(북마크 많은 순)으로 페이지네이션해 반환합니다.
			- `genre`는 `?genre=ACTION&genre=ROMANCE`처럼 반복 파라미터로 여러 개 지정할 수 있으며, 요청한 모든 장르를 가진 콘텐츠만 반환합니다.
			- `keyword` 지정 시(genre 없음): TMDB API로 검색합니다.
			- 둘 다 없을 시: TMDB 인기 영화를 반환합니다.
			- `genre`와 `keyword`가 동시에 들어오면 `genre`가 우선합니다.
			"""
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "검색 성공", useReturnTypeSchema = true)
	})
	ResponseEntity<?> searchContent(
		@Parameter(description = "검색 키워드", example = "눈물의 여왕")
		String keyword,
		@Parameter(description = "장르 필터. 반복 파라미터로 여러 개 지정 가능하며 AND 조건으로 검색합니다.", example = "ACTION")
		List<SearchGenre> genres,
		@Parameter(description = "페이지 번호 (1부터 시작)", example = "1")
		int cursor,
		@Parameter(description = "페이지당 결과 수", example = "20")
		int size
	);

}
