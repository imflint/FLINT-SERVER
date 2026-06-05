package kr.flint.admin.domain.collection.controller.spec;

import org.springframework.http.ResponseEntity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.flint.admin.domain.collection.dto.request.AdminCollectionUpdateReq;
import kr.flint.admin.domain.collection.dto.request.AdminCollectionVisibility;
import kr.flint.admin.domain.collection.dto.response.AdminCollectionDetailRes;
import kr.flint.admin.domain.collection.dto.response.AdminCollectionSummaryRes;
import kr.flint.collection.domain.CollectionModerationStatus;
import kr.flint.shared.dto.PaginationResponse;
import kr.flint.shared.dto.response.SuccessResponse;
import kr.flint.shared.exception.ProblemDetail;

@Tag(name = "Collection Admin", description = "컬렉션 관리 API")
public interface AdminCollectionControllerDocs {

    @Operation(summary = "컬렉션 목록 조회", description = "관리자가 컬렉션을 검색하고 목록을 page/size 기반으로 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "컬렉션 목록 조회 성공", useReturnTypeSchema = true)
    })
    ResponseEntity<SuccessResponse<PaginationResponse<AdminCollectionSummaryRes>>> getCollections(
        @Parameter(hidden = true) Long adminId,
        String keyword,
        AdminCollectionVisibility visibility,
        CollectionModerationStatus moderationStatus,
        Integer page,
        Integer size
    );

    @Operation(summary = "컬렉션 상세 조회", description = "관리자가 컬렉션 기본 정보와 포함 콘텐츠를 조회합니다. 콘텐츠별 커스텀 이미지는 customImageUrls 배열로 반환됩니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "컬렉션 상세 조회 성공", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "404", description = "컬렉션 없음", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    ResponseEntity<SuccessResponse<AdminCollectionDetailRes>> getCollection(
        @Parameter(hidden = true) Long adminId,
        Long collectionId
    );

    @Operation(summary = "컬렉션 수정", description = "관리자가 컬렉션 기본 정보와 포함 콘텐츠를 전체 교체 방식으로 수정합니다. contentList[].customImages는 요청 순서대로 저장되며 기존 콘텐츠별 이미지를 함께 교체합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "컬렉션 수정 성공", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "잘못된 컬렉션 수정 요청", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "404", description = "컬렉션 또는 콘텐츠 없음", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    ResponseEntity<SuccessResponse<AdminCollectionDetailRes>> updateCollection(
        @Parameter(hidden = true) Long adminId,
        Long collectionId,
        AdminCollectionUpdateReq request
    );
}
