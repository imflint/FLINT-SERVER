package kr.flint.admin.domain.terms.controller.spec;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.flint.admin.domain.terms.dto.request.TermsCreateReq;
import kr.flint.admin.domain.terms.dto.request.TermsListSort;
import kr.flint.shared.dto.response.SuccessResponse;
import kr.flint.shared.exception.ProblemDetail;
import kr.flint.terms.domain.TermsType;
import kr.flint.terms.dto.response.TermsRes;

@Tag(name = "Terms Admin", description = "약관 관리 API")
public interface TermsAdminControllerDocs {

    @Operation(summary = "약관 목록 조회", description = "관리자가 전체 약관을 조회하고 약관 유형, 버전순, 종류순 조건으로 정렬합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "약관 목록 조회 성공", useReturnTypeSchema = true),
        @ApiResponse(
            responseCode = "403",
            description = "관리 권한 없음",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))
        )
    })
    ResponseEntity<SuccessResponse<List<TermsRes>>> getTerms(
        @Parameter(hidden = true) Long adminId,
        TermsType type,
        TermsListSort sortBy,
        Sort.Direction direction
    );

    @Operation(summary = "약관 버전 생성", description = "관리자가 새 약관 버전을 생성합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "약관 생성 성공", useReturnTypeSchema = true),
        @ApiResponse(
            responseCode = "403",
            description = "관리 권한 없음",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class))
        )
    })
    ResponseEntity<SuccessResponse<TermsRes>> createTerms(Long adminId, TermsCreateReq request);
}
