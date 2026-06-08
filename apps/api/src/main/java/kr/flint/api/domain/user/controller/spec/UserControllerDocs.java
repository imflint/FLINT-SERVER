package kr.flint.api.domain.user.controller.spec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import kr.flint.api.domain.content.dto.GetContentListRes;
import kr.flint.api.domain.user.dto.request.UpdateNicknameReq;
import kr.flint.api.domain.user.dto.request.UpdateProfileImageReq;
import kr.flint.api.domain.user.dto.response.MyProfileRes;
import kr.flint.api.domain.user.dto.response.UserCollectionsRes;
import kr.flint.api.domain.user.dto.response.UserKeywordsRes;
import kr.flint.api.domain.user.dto.response.UserProfileRes;
import kr.flint.shared.dto.response.SuccessResponse;
import kr.flint.user.domain.NicknamePolicy;
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
            @Parameter(description = "확인할 닉네임 (2-8자, 한글/영문/숫자 혼용 가능)", example = "플린트")
            @NotBlank(message = NicknamePolicy.MESSAGE)
            @Size(min = NicknamePolicy.MIN_LENGTH, max = NicknamePolicy.MAX_LENGTH, message = NicknamePolicy.MESSAGE)
            @Pattern(regexp = NicknamePolicy.REGEX, message = NicknamePolicy.MESSAGE)
            String nickname
    );

    // === 본인 조회 (인증 필수) ===

    @Operation(summary = "내 프로필 조회 - 호주",
               description = "로그인한 사용자 본인의 프로필 정보와 필수 약관 추가 동의 필요 여부를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "프로필 조회 성공", useReturnTypeSchema = true)
    })
    ResponseEntity<SuccessResponse<MyProfileRes>> getMyProfile(
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
               description = "로그인한 사용자의 닉네임을 변경합니다. 2-8자, 한글/영문/숫자만 허용됩니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "닉네임 변경 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "409", description = "이미 사용 중인 닉네임")
    })
    ResponseEntity<SuccessResponse<Void>> updateNickname(
            @Parameter(hidden = true) Long userId,
            @Valid @RequestBody UpdateNicknameReq request
    );

    @Operation(summary = "내 취향 키워드 재계산 - 재민",
               description = """
                   GPT를 호출하여 로그인한 사용자의 취향 키워드를 다시 계산합니다.

                   - **사용자 수동 트리거 전용** API. 호출 가능 조건:
                     마지막 재계산 이후 **새로 저장한 컨텐츠가 20개 이상 누적**된 경우에만 호출 가능합니다.
                     (컨텐츠 북마크 ON 시에만 카운트 — OFF는 카운트되지 않음)
                   - 조건 미달 시 `400 USER.KEYWORD_RECALC_NOT_READY` 반환.
                   - 성공 시 카운터는 0으로 리셋되어 다음 20개 누적까지 다시 비활성 상태로 돌아갑니다.
                   - 응답 키워드에는 GPT가 산출한 비율(percentage)이 포함됩니다.
                   """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "취향 키워드 재계산 성공", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "재계산 가능 조건 미달 (신규 저장 작품 20개 미달)")
    })
    ResponseEntity<SuccessResponse<Void>> recalculateKeyword(
            @Parameter(hidden = true) Long userId
    );

    @Operation(summary = "프로필 이미지 수정",
               description = "로그인한 사용자의 프로필 이미지를 등록 또는 수정합니다. S3 presigned URL로 업로드 후 반환받은 키를 전달합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "프로필 이미지 수정 성공", useReturnTypeSchema = true)
    })
    ResponseEntity<SuccessResponse<Void>> updateProfileImage(
            @Parameter(hidden = true) Long userId,
            @Valid @RequestBody UpdateProfileImageReq request
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
		@Parameter(hidden = true) Long currentUserId,
		@Parameter(description = "다른 사용자 ID", example = "1")
		@PathVariable Long userId
	);
}
