package kr.flint.admin.domain.user.controller.spec;

import org.springframework.http.ResponseEntity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.flint.admin.domain.user.dto.response.AdminUserStatisticsRes;
import kr.flint.shared.dto.response.SuccessResponse;
import kr.flint.shared.exception.ProblemDetail;

@Tag(name = "User Admin", description = "사용자 관리 API")
public interface AdminUserStatisticsControllerDocs {

    @Operation(summary = "현재 활성 사용자 수 조회", description = "탈퇴 사용자와 현재 정지 중인 사용자를 제외한 활성 사용자 수를 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "사용자 통계 조회 성공", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "403", description = "관리 권한 없음", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    ResponseEntity<SuccessResponse<AdminUserStatisticsRes>> getStatistics(@Parameter(hidden = true) Long adminUserId);
}
