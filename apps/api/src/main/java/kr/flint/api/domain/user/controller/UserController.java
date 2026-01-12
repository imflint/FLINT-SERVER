package kr.flint.api.domain.user.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import kr.flint.api.domain.user.controller.spec.UserControllerDocs;
import kr.flint.api.domain.user.dto.response.UserBookmarkedCollectionsResponse;
import kr.flint.api.domain.user.dto.response.UserCollectionsResponse;
import kr.flint.api.domain.user.dto.response.UserKeywordsResponse;
import kr.flint.api.domain.user.dto.response.UserProfileResponse;
import kr.flint.api.domain.user.service.UserQueryFacade;
import kr.flint.api.global.security.UserPrincipal;
import kr.flint.shared.dto.response.SuccessCode;
import kr.flint.shared.dto.response.SuccessResponse;
import kr.flint.user.dto.response.NicknameCheckResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController implements UserControllerDocs {

    private final UserQueryFacade userQueryFacade;

    @Override
    @GetMapping("/nickname/check")
    public ResponseEntity<SuccessResponse<NicknameCheckResponse>> checkNickname(
            @RequestParam
            @NotBlank(message = "닉네임은 필수입니다.")
            @Size(min = 2, max = 10, message = "닉네임은 2자 이상 10자 이하여야 합니다.")
            @Pattern(regexp = "^[a-zA-Z0-9가-힣_]+$", message = "닉네임은 영문, 숫자, 한글, 밑줄만 사용 가능합니다.")
            String nickname
    ) {
        return ResponseEntity.ok(
                SuccessResponse.of(SuccessCode.SUCCESS_NICKNAME_CHECK, userQueryFacade.checkNickname(nickname))
        );
    }

    @Override
    @GetMapping("/{userId}")
    public ResponseEntity<SuccessResponse<UserProfileResponse>> getUserProfile(
            @PathVariable Long userId
    ) {
        return ResponseEntity.ok(
                SuccessResponse.of(SuccessCode.SUCCESS_FETCH, userQueryFacade.getUserProfile(userId))
        );
    }

    @Override
    @GetMapping("/{userId}/keywords")
    public ResponseEntity<SuccessResponse<UserKeywordsResponse>> getUserKeywords(
            @PathVariable Long userId
    ) {
        return ResponseEntity.ok(
                SuccessResponse.of(SuccessCode.SUCCESS_KEYWORDS_FETCH, userQueryFacade.getUserKeywords(userId))
        );
    }

    @Override
    @GetMapping("/{userId}/collections")
    public ResponseEntity<SuccessResponse<UserCollectionsResponse>> getUserCollections(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long userId
    ) {
        boolean isOwner = isOwner(principal, userId);
        return ResponseEntity.ok(
                SuccessResponse.of(SuccessCode.SUCCESS_COLLECTIONS_FETCH, userQueryFacade.getUserCollections(userId, isOwner))
        );
    }

    @Override
    @GetMapping("/{userId}/bookmarked-collections")
    public ResponseEntity<SuccessResponse<UserBookmarkedCollectionsResponse>> getUserBookmarkedCollections(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long userId
    ) {
        boolean isOwner = isOwner(principal, userId);
        return ResponseEntity.ok(
                SuccessResponse.of(SuccessCode.SUCCESS_COLLECTIONS_FETCH, userQueryFacade.getUserBookmarkedCollections(userId, isOwner))
        );
    }

    // 본인 여부 확인 (비로그인 시 false)
    private boolean isOwner(UserPrincipal principal, Long userId) {
        return principal != null && principal.userId().equals(userId);
    }
}
