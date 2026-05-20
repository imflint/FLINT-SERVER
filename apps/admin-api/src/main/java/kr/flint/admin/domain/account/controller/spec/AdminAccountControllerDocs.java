package kr.flint.admin.domain.account.controller.spec;

import org.springframework.http.ResponseEntity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.flint.admin.domain.account.dto.request.AdminAccountUpdateReq;
import kr.flint.admin.domain.account.dto.response.AdminMeRes;
import kr.flint.shared.dto.response.SuccessResponse;
import kr.flint.shared.exception.ProblemDetail;

@Tag(name = "Admin Account", description = "관리자 본인 계정 API")
public interface AdminAccountControllerDocs {

    @Operation(summary = "관리자 본인 정보 조회", description = "현재 로그인한 관리자 계정 정보를 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "관리자 본인 정보 조회 성공", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "403", description = "관리 권한 없음", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "404", description = "관리자 계정 없음", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    ResponseEntity<SuccessResponse<AdminMeRes>> getMe(@Parameter(hidden = true) Long adminId);

    @Operation(summary = "관리자 본인 정보 수정", description = "현재 로그인한 관리자의 로그인 ID와 비밀번호를 수정합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "관리자 본인 정보 수정 성공", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "401", description = "현재 비밀번호 불일치", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "403", description = "관리 권한 없음", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "404", description = "관리자 계정 없음", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "409", description = "이미 사용 중인 관리자 로그인 ID", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    ResponseEntity<SuccessResponse<AdminMeRes>> updateMe(
        @Parameter(hidden = true) Long adminId,
        AdminAccountUpdateReq request
    );
}
