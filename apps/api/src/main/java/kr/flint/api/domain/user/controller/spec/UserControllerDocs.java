package kr.flint.api.domain.user.controller.spec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import kr.flint.api.domain.content.dto.GetContentListRes;
import kr.flint.api.domain.user.dto.request.UpdateNicknameReq;
import kr.flint.api.domain.user.dto.response.UserCollectionsRes;
import kr.flint.api.domain.user.dto.response.UserKeywordsRes;
import kr.flint.api.domain.user.dto.response.UserProfileRes;
import kr.flint.shared.dto.response.SuccessResponse;
import kr.flint.user.dto.response.NicknameCheckResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "User", description = "사용자 API")
public interface UserControllerDocs {

    @Operation(summary = "닉네임 중복 체크 - 호주",
               description = "닉네임 사용 가능 여부를 확인합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "닉네임 체크 성공", useReturnTypeSchema = true)
    })
    ResponseEntity<SuccessResponse<NicknameCheckResponse>> checkNickname(
            @Parameter(description = "확인할 닉네임 (2-10자, 영문/숫자/한글/밑줄만 가능)", example = "플린트")
            @Size(min = 2, max = 10, message = "닉네임은 2자 이상 10자 이하여야 합니다.")
            @Pattern(regexp = "^[a-zA-Z0-9가-힣_]+$", message = "닉네임은 영문, 숫자, 한글, 밑줄만 사용 가능합니다.")
            String nickname
    );

    // === 본인 조회 (인증 필수) ===

    @Operation(summary = "내 프로필 조회 - 호주",
               description = "로그인한 사용자 본인의 프로필 정보를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "프로필 조회 성공", useReturnTypeSchema = true)
    })
    ResponseEntity<SuccessResponse<UserProfileRes>> getMyProfile(
            @Parameter(hidden = true) Long userId
    );

    @Operation(summary = "내 취향 키워드 조회 - 호주",
               description = "로그인한 사용자 본인의 취향 키워드 목록을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "취향 키워드 조회 성공", useReturnTypeSchema = true)
    })
    ResponseEntity<SuccessResponse<UserKeywordsRes>> getMyKeywords(
            @Parameter(hidden = true) Long userId
    );

    @Operation(summary = "내 컬렉션 조회 - 호주",
               description = "로그인한 사용자 본인이 생성한 모든 컬렉션(공개+비공개)을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "컬렉션 조회 성공", useReturnTypeSchema = true)
    })
    ResponseEntity<SuccessResponse<UserCollectionsRes>> getMyCollections(
            @Parameter(hidden = true) Long userId
    );

    @Operation(summary = "내 북마크 컬렉션 조회 - 호주",
               description = "로그인한 사용자 본인이 북마크한 모든 컬렉션을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "북마크 컬렉션 조회 성공", useReturnTypeSchema = true)
    })
    ResponseEntity<SuccessResponse<UserCollectionsRes>> getMyBookmarkedCollections(
            @Parameter(hidden = true) Long userId
    );

    @Operation(summary = "닉네임 변경",
               description = "로그인한 사용자의 닉네임을 변경합니다. 2-10자, 영문/숫자/한글/밑줄만 허용됩니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "닉네임 변경 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "409", description = "이미 사용 중인 닉네임")
    })
    ResponseEntity<SuccessResponse<Void>> updateNickname(
            @Parameter(hidden = true) Long userId,
            @Valid @RequestBody UpdateNicknameReq request
    );

    @Operation(summary = "내 취향 키워드 재계산 - 재민",
               description = "GPT를 호출하여 로그인한 사용자의 취향 키워드를 다시 계산합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "취향 키워드 재계산 성공", useReturnTypeSchema = true)
    })
    ResponseEntity<SuccessResponse<Void>> recalculateKeyword(
            @Parameter(hidden = true) Long userId
    );

    // === 타인 조회 (인증 선택) ===

    @Operation(summary = "사용자 프로필 조회 - 호주",
               description = "특정 사용자의 프로필 정보를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "프로필 조회 성공", useReturnTypeSchema = true)
    })
    ResponseEntity<SuccessResponse<UserProfileRes>> getUserProfile(
            @Parameter(description = "사용자 ID", example = "123456789")
            @PathVariable Long userId
    );

    @Operation(summary = "사용자 취향 키워드 조회", description = "특정 사용자의 취향 키워드 목록을 조회합니다. - 호주")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "취향 키워드 조회 성공", useReturnTypeSchema = true)
    })
    ResponseEntity<SuccessResponse<UserKeywordsRes>> getUserKeywords(
            @Parameter(description = "사용자 ID", example = "123456789")
            @PathVariable Long userId
    );

    @Operation(summary = "사용자 컬렉션 조회 - 호주",
               description = "특정 사용자가 생성한 공개 컬렉션을 조회합니다. 본인의 북마크 여부가 포함")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "컬렉션 조회 성공", useReturnTypeSchema = true)
    })
    ResponseEntity<SuccessResponse<UserCollectionsRes>> getUserCollections(
            @Parameter(hidden = true) Long currentUserId,
            @Parameter(description = "사용자 ID", example = "123456789")
            @PathVariable Long userId
    );

    @Operation(summary = "사용자 북마크 컬렉션 조회 - 호주",
               description = "특정 사용자가 북마크한 공개 컬렉션을 조회합니다. 본인의 북마크 여부가 포함")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "북마크 컬렉션 조회 성공", useReturnTypeSchema = true)
    })
    ResponseEntity<SuccessResponse<UserCollectionsRes>> getUserBookmarkedCollections(
            @Parameter(hidden = true) Long currentUserId,
            @Parameter(description = "사용자 ID", example = "123456789")
            @PathVariable Long userId
    );

	@Operation(
		summary = "다른 사용자의 북마크한 콘텐츠 목록 조회 - 재민",
		description = "다른 사용자의 북마크한 콘텐츠 목록을 조회합니다."
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공", useReturnTypeSchema = true)
	})
	@GetMapping("/{userId}/bookmarked-contents")
	ResponseEntity<SuccessResponse<GetContentListRes>> getUserBookmarkedContents(
		@Parameter(description = "다른 사용자 ID", example = "1")
		@PathVariable Long userId
	);
}
