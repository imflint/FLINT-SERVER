package kr.flint.api.domain.terms.controller.spec;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.flint.api.domain.terms.dto.request.TermsAgreementReq;
import kr.flint.shared.dto.response.SuccessResponse;
import kr.flint.shared.exception.ProblemDetail;
import kr.flint.terms.domain.TermsContext;
import kr.flint.terms.domain.TermsType;
import kr.flint.terms.dto.response.TermsListRes;
import kr.flint.terms.dto.response.TermsRes;

@Tag(name = "Terms", description = "약관 API")
public interface TermsControllerDocs {

	@Operation(summary = "약관 목록 조회", description = "현재 활성 약관을 context와 유형별 최신 버전으로 조회합니다. context 기본값은 SIGNUP입니다.")
	@ApiResponses({
		@ApiResponse(
			responseCode = "200",
			description = "약관 목록 조회 성공",
			content = @Content(schema = @Schema(implementation = TermsListRes.class))
		)
	})
	ResponseEntity<SuccessResponse<TermsListRes>> getTerms(TermsContext context, TermsType type);

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

	@Operation(
		summary = "로그인 후 약관 재동의 저장",
		description = "로그인한 사용자가 새로 활성화된 SIGNUP 약관에 재동의할 때 사용합니다. 초기 회원가입 동의는 POST /auth/signup의 agreedTermsIds로 처리합니다."
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "약관 동의 저장 성공", useReturnTypeSchema = true),
		@ApiResponse(
			responseCode = "400",
			description = "필수 약관 미동의 또는 유효하지 않은 약관 ID",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class))
		)
	})
	ResponseEntity<SuccessResponse<Void>> agreeTerms(
		@Parameter(hidden = true) Long userId,
		@Valid @RequestBody TermsAgreementReq request
	);
}
