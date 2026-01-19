package kr.flint.api.domain.content.controller.spec;

import java.util.List;

import org.springframework.http.ResponseEntity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
	ResponseEntity<SuccessResponse<List<GetOttResponse>>> getOttList(
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
		description = "키워드로 콘텐츠를 검색합니다. TMDB API를 통해 검색 결과를 반환합니다.",
        deprecated = true
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "검색 성공", useReturnTypeSchema = true)
	})
	ResponseEntity<?> searchContent(
		@Parameter(description = "검색 키워드", example = "눈물의 여왕")
		String keyword,
		@Parameter(description = "페이지 번호 (1부터 시작)", example = "1")
		int cursor,
		@Parameter(description = "페이지당 결과 수", example = "20")
		int size
	);
}
