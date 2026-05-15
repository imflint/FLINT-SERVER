package kr.flint.admin.domain.report.controller.spec;

import org.springframework.http.ResponseEntity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.flint.admin.domain.report.dto.request.AdminCollectionReportResolutionReq;
import kr.flint.admin.domain.report.dto.response.AdminCollectionReportDetailRes;
import kr.flint.admin.domain.report.dto.response.AdminCollectionReportSummaryRes;
import kr.flint.collection.domain.ReportStatus;
import kr.flint.shared.dto.PaginationResponse;
import kr.flint.shared.dto.response.SuccessResponse;
import kr.flint.shared.exception.ProblemDetail;

@Tag(name = "Collection Report Admin", description = "컬렉션 신고 관리 API")
public interface AdminCollectionReportControllerDocs {

    @Operation(summary = "컬렉션 신고 목록 조회", description = "관리자가 컬렉션 신고 목록을 cursor 기반으로 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "컬렉션 신고 목록 조회 성공", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "403", description = "관리 권한 없음", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    ResponseEntity<SuccessResponse<PaginationResponse<AdminCollectionReportSummaryRes>>> getReports(
        @Parameter(hidden = true) Long adminId,
        ReportStatus status,
        Long cursor,
        Integer size
    );

    @Operation(summary = "컬렉션 신고 상세 조회", description = "신고 정보와 신고 대상 컬렉션의 콘텐츠 목록을 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "컬렉션 신고 상세 조회 성공", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "404", description = "신고 없음", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    ResponseEntity<SuccessResponse<AdminCollectionReportDetailRes>> getReport(
        @Parameter(hidden = true) Long adminId,
        Long reportId
    );

    @Operation(summary = "컬렉션 신고 처리", description = "관리자가 컬렉션 신고에 대한 컬렉션 조치와 사용자 조치를 확정합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "컬렉션 신고 처리 성공", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "409", description = "이미 처리된 신고", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    ResponseEntity<SuccessResponse<Void>> resolveReport(
        @Parameter(hidden = true) Long adminId,
        Long reportId,
        AdminCollectionReportResolutionReq request
    );
}
