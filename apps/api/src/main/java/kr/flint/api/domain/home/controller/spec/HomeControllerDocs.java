package kr.flint.api.domain.home.controller.spec;

import org.springframework.http.ResponseEntity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.flint.api.domain.home.dto.response.RecommendedCollectionsRes;
import kr.flint.shared.dto.response.SuccessResponse;

@Tag(name = "Home", description = "홈 화면 API")
public interface HomeControllerDocs {

    @Operation(
        summary = "추천 컬렉션 조회 - 호주",
        description = "사용자 취향 키워드 기반 추천 컬렉션 목록(최대 10개)을 조회합니다."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "추천 컬렉션 조회 성공",
            useReturnTypeSchema = true
        )
    })
    ResponseEntity<SuccessResponse<RecommendedCollectionsRes>> getRecommendedCollections(Long userId);
}
