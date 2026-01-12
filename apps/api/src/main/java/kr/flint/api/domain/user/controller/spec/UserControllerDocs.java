package kr.flint.api.domain.user.controller.spec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.flint.api.domain.user.dto.response.UserBookmarkedCollectionsRes;
import kr.flint.api.domain.user.dto.response.UserCollectionsRes;
import kr.flint.api.domain.user.dto.response.UserKeywordsRes;
import kr.flint.api.domain.user.dto.response.UserProfileRes;
import kr.flint.api.global.security.UserPrincipal;
import kr.flint.shared.dto.response.SuccessResponse;
import kr.flint.user.dto.response.NicknameCheckResponse;
import org.springframework.http.ResponseEntity;

@Tag(name = "User", description = "사용자 API")
public interface UserControllerDocs {

    @Operation(
            summary = "닉네임 중복 체크",
            description = "닉네임 사용 가능 여부를 확인합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "닉네임 체크 성공"
            )
    })
    ResponseEntity<SuccessResponse<NicknameCheckResponse>> checkNickname(
            @Parameter(description = "확인할 닉네임", example = "my_nickname") String nickname
    );

    @Operation(
            summary = "사용자 프로필 조회",
            description = "특정 사용자의 기본 프로필 정보(닉네임, 프로필 이미지, 플리너 여부)를 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "프로필 조회 성공"
            )
    })
    ResponseEntity<SuccessResponse<UserProfileRes>> getUserProfile(
            @Parameter(description = "사용자 ID", example = "123456789") Long userId
    );

    @Operation(
            summary = "사용자 취향 키워드 조회",
            description = "특정 사용자의 취향 키워드 목록을 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "취향 키워드 조회 성공"
            )
    })
    ResponseEntity<SuccessResponse<UserKeywordsRes>> getUserKeywords(
            @Parameter(description = "사용자 ID", example = "123456789") Long userId
    );

    @Operation(
            summary = "사용자 생성 컬렉션 조회",
            description = "특정 사용자가 생성한 컬렉션 목록을 조회합니다. 본인 조회 시 전체 컬렉션, 타인 조회 시 공개 컬렉션만 반환됩니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "컬렉션 조회 성공"
            )
    })
    ResponseEntity<SuccessResponse<UserCollectionsRes>> getUserCollections(
            @Parameter(hidden = true) UserPrincipal principal,
            @Parameter(description = "사용자 ID", example = "123456789") Long userId
    );

    @Operation(
            summary = "사용자 북마크 컬렉션 조회",
            description = "특정 사용자가 북마크한 컬렉션 목록을 조회합니다. 본인 조회 시 전체 컬렉션, 타인 조회 시 공개 컬렉션만 반환됩니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "북마크 컬렉션 조회 성공"
            )
    })
    ResponseEntity<SuccessResponse<UserBookmarkedCollectionsRes>> getUserBookmarkedCollections(
            @Parameter(hidden = true) UserPrincipal principal,
            @Parameter(description = "사용자 ID", example = "123456789") Long userId
    );
}
