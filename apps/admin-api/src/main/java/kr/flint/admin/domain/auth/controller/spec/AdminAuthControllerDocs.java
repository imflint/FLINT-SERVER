package kr.flint.admin.domain.auth.controller.spec;

import org.springframework.http.ResponseEntity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.flint.admin.domain.auth.dto.request.AdminLoginReq;
import kr.flint.admin.domain.auth.dto.response.AdminLoginRes;
import kr.flint.shared.dto.response.SuccessResponse;
import kr.flint.shared.exception.ProblemDetail;

@Tag(name = "Admin Auth", description = "관리자 인증 API")
public interface AdminAuthControllerDocs {

	@Operation(summary = "관리자 로그인", description = "관리자 계정 정보로 Admin API Access Token과 Refresh Token을 발급합니다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "관리자 로그인 성공", useReturnTypeSchema = true),
		@ApiResponse(
			responseCode = "400",
			description = "잘못된 요청",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class))
		),
		@ApiResponse(
			responseCode = "401",
			description = "관리자 인증 실패",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class))
		),
		@ApiResponse(
			responseCode = "403",
			description = "관리자 권한 없음",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class))
		)
	})
	ResponseEntity<SuccessResponse<AdminLoginRes>> login(AdminLoginReq request);
}
