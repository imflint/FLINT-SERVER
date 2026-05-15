package kr.flint.admin.domain.content.controller.spec;

import org.springframework.http.ResponseEntity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.flint.admin.domain.content.dto.request.AdminContentUpdateReq;
import kr.flint.admin.domain.content.dto.response.AdminContentRes;
import kr.flint.shared.dto.response.SuccessResponse;
import kr.flint.shared.exception.ProblemDetail;

@Tag(name = "Content Admin", description = "콘텐츠 관리 API")
public interface AdminContentControllerDocs {

    @Operation(summary = "콘텐츠 수정", description = "관리자가 영화/TV 콘텐츠 메타데이터와 장르를 수정합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "콘텐츠 수정 성공", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "404", description = "콘텐츠 없음", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    ResponseEntity<SuccessResponse<AdminContentRes>> updateContent(
        @Parameter(hidden = true) Long adminId,
        Long contentId,
        AdminContentUpdateReq request
    );
}
