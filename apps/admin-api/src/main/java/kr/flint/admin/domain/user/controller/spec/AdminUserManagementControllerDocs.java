package kr.flint.admin.domain.user.controller.spec;

import java.time.LocalDate;

import org.springframework.http.ResponseEntity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.flint.admin.domain.user.dto.request.AdminUserModerationReq;
import kr.flint.admin.domain.user.dto.response.AdminUserDetailRes;
import kr.flint.admin.domain.user.dto.response.AdminUserSummaryRes;
import kr.flint.shared.dto.PaginationResponse;
import kr.flint.shared.dto.response.SuccessResponse;
import kr.flint.shared.exception.ProblemDetail;
import kr.flint.user.domain.UserStatus;

@Tag(name = "User Admin", description = "회원 관리 API")
public interface AdminUserManagementControllerDocs {

    @Operation(summary = "회원 목록 조회", description = "관리자가 회원을 검색하고 목록을 page/size 기반으로 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "회원 목록 조회 성공", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "403", description = "관리 권한 없음", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    ResponseEntity<SuccessResponse<PaginationResponse<AdminUserSummaryRes>>> getUsers(
        @Parameter(hidden = true) Long adminId,
        String keyword,
        UserStatus status,
        LocalDate createdFrom,
        LocalDate createdTo,
        Integer page,
        Integer size
    );

    @Operation(summary = "회원 상세 조회", description = "관리자가 회원 기본 정보, 현재 제재 상태, 최근 제재 이력을 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "회원 상세 조회 성공", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "404", description = "회원 없음", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    ResponseEntity<SuccessResponse<AdminUserDetailRes>> getUser(
        @Parameter(hidden = true) Long adminId,
        Long userId
    );

    @Operation(summary = "회원 제재", description = "관리자가 회원에게 경고, 업로드 제한, 이용 정지 조치를 적용합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "회원 제재 성공", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "잘못된 회원 제재 요청", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "404", description = "회원 없음", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    ResponseEntity<SuccessResponse<AdminUserDetailRes>> moderateUser(
        @Parameter(hidden = true) Long adminId,
        Long userId,
        AdminUserModerationReq request
    );
}
