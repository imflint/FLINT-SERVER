package kr.flint.api.admin.domain.content.controller.spec;

import org.springframework.http.ResponseEntity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.flint.api.admin.domain.content.dto.request.AdminContentUpdateReq;
import kr.flint.api.admin.domain.content.dto.request.AdminContentCreateReq;
import kr.flint.api.admin.domain.content.dto.response.AdminContentRes;
import kr.flint.content.domain.MediaType;
import kr.flint.shared.dto.PaginationResponse;
import kr.flint.shared.dto.response.SuccessResponse;
import kr.flint.shared.exception.ProblemDetail;

@Tag(name = "Content Admin", description = "콘텐츠 관리 API")
public interface AdminContentControllerDocs {

    @Operation(summary = "콘텐츠 목록 조회", description = "관리자가 콘텐츠를 검색하고 목록을 cursor 방식으로 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "콘텐츠 목록 조회 성공", useReturnTypeSchema = true)
    })
    ResponseEntity<SuccessResponse<PaginationResponse<AdminContentRes>>> getContents(
        @Parameter(hidden = true) Long adminId,
        String keyword,
        MediaType mediaType,
        Long cursor,
        Integer size
    );

    @Operation(summary = "수동 콘텐츠 등록", description = "TMDB에 없는 영화/TV 콘텐츠를 등록합니다. TMDB ID 없이 새 콘텐츠 ID를 발급하며 기존 작품과 제목이 같아도 병합하지 않습니다. TMDB 동기화 및 언어 정리 대상에서 제외됩니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "콘텐츠 등록 성공", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "필수 입력 누락, 잘못된 종류·연도·장르"),
        @ApiResponse(responseCode = "401", description = "관리자 인증 필요"),
        @ApiResponse(responseCode = "403", description = "관리자 권한 없음"),
        @ApiResponse(responseCode = "409", description = "표준 장르 이관 미완료")
    })
    ResponseEntity<SuccessResponse<AdminContentRes>> createContent(
        @Parameter(hidden = true) Long adminId,
        AdminContentCreateReq request
    );

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
