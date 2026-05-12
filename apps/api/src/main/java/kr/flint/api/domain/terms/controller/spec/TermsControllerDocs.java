package kr.flint.api.domain.terms.controller.spec;

import org.springframework.http.ResponseEntity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.flint.shared.dto.response.SuccessResponse;
import kr.flint.shared.exception.ProblemDetail;
import kr.flint.terms.domain.TermsType;
import kr.flint.terms.dto.response.TermsListRes;
import kr.flint.terms.dto.response.TermsRes;

@Tag(name = "Terms", description = "약관 API")
public interface TermsControllerDocs {

	@Operation(summary = "약관 목록 조회", description = "현재 활성 약관을 유형별 최신 버전으로 조회합니다. 파라미터가 없으면 전부 조회")
	@ApiResponses({
		@ApiResponse(
			responseCode = "200",
			description = "약관 목록 조회 성공",
			content = @Content(schema = @Schema(implementation = TermsListRes.class))
		)
	})
	ResponseEntity<SuccessResponse<TermsListRes>> getTerms(TermsType type);

	@Operation(summary = "약관 상세 조회", description = "약관 ID로 약관 본문을 조회합니다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "약관 상세 조회 성공", useReturnTypeSchema = true),
		@ApiResponse(
			responseCode = "404",
			description = "약관 없음",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class))
		)
	})
	ResponseEntity<SuccessResponse<TermsRes>> getTerms(Long termsId);
}
