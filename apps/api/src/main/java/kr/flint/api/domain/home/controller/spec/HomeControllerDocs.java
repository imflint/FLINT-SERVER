package kr.flint.api.domain.home.controller.spec;

import org.springframework.http.ResponseEntity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.flint.api.domain.home.dto.response.PopularCollectionsRes;
import kr.flint.api.domain.home.dto.response.RecommendedCollectionsRes;
import kr.flint.shared.dto.response.SuccessResponse;

@Tag(name = "Home", description = "홈 화면 API")
public interface HomeControllerDocs {

    @Operation(
        summary = "추천 컬렉션 조회 - 호주",
        description = "사용자 취향 키워드 기반 추천 컬렉션 목록(최대 5개)을 조회합니다."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "추천 컬렉션 조회 성공",
            useReturnTypeSchema = true
        )
    })
    ResponseEntity<SuccessResponse<RecommendedCollectionsRes>> getRecommendedCollections(Long userId);

    @Operation(
        summary = "인기 컬렉션 조회 - 호주",
        description = """
            최근 1주일 동안 컬렉션이 추가로 저장된 횟수(증가량) 기준으로 인기 컬렉션을 최대 10개까지 반환합니다.

            - 좌우 스크롤 UI 용도 (페이지네이션 없음)
            - 최근 1주일 증가량이 동률이거나 0건인 항목은 **총 북마크 수** 로 자연스럽게 fallback 정렬됩니다.
              (서비스 초기에 충분한 주간 데이터가 없을 때도 의미 있는 결과를 반환하기 위함)
            - 비공개 컬렉션, 본문/사유가 비어있는 컬렉션은 제외됩니다.
            """
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "인기 컬렉션 조회 성공",
            content = @Content(
                schema = @Schema(implementation = SuccessResponse.class),
                examples = @ExampleObject(
                    name = "성공 예시",
                    value = """
                        {
                          "code": "SUCCESS_POPULAR_COLLECTIONS_FETCH",
                          "message": "인기 컬렉션 조회가 완료되었습니다",
                          "data": {
                            "collections": [
                              {
                                "id": "800388257884431200",
                                "thumbnailUrl": "https://cdn.flint.kr/collection/cover/800388.jpg",
                                "title": "주말에 보기 좋은 한국 영화",
                                "imageList": [
                                  "https://cdn.flint.kr/content/poster/100.jpg",
                                  "https://cdn.flint.kr/content/poster/101.jpg"
                                ],
                                "nickname": "플린트",
                                "profileImageUrl": "https://cdn.flint.kr/user/profile/123.jpg"
                              },
                              {
                                "id": "800388257884431201",
                                "thumbnailUrl": "https://cdn.flint.kr/collection/cover/800389.jpg",
                                "title": "비 오는 날 듣기 좋은 OST 영화",
                                "imageList": [
                                  "https://cdn.flint.kr/content/poster/102.jpg",
                                  "https://cdn.flint.kr/content/poster/103.jpg"
                                ],
                                "nickname": "수채한",
                                "profileImageUrl": "https://cdn.flint.kr/user/profile/124.jpg"
                              }
                            ]
                          }
                        }
                        """
                )
            )
        )
    })
    ResponseEntity<SuccessResponse<PopularCollectionsRes>> getPopularCollections();
}
