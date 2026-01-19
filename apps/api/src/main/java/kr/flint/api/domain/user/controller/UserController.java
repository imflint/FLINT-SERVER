package kr.flint.api.domain.user.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import kr.flint.api.domain.user.controller.spec.UserControllerDocs;
import kr.flint.api.domain.user.dto.response.UserBookmarkedCollectionsRes;
import kr.flint.api.domain.user.dto.response.UserCollectionsRes;
import kr.flint.api.domain.user.dto.response.UserKeywordsRes;
import kr.flint.api.domain.user.dto.response.UserProfileRes;
import kr.flint.api.domain.user.service.UserCommandFacade;
import kr.flint.api.domain.user.service.UserQueryFacade;
import kr.flint.api.global.security.annotation.CurrentUser;
import kr.flint.shared.dto.response.SuccessCode;
import kr.flint.shared.dto.response.SuccessResponse;
import kr.flint.user.dto.response.NicknameCheckResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController implements UserControllerDocs {

    private final UserQueryFacade userQueryFacade;
	private final UserCommandFacade userCommandFacade;

    @GetMapping("/nickname/check")
    public ResponseEntity<SuccessResponse<NicknameCheckResponse>> checkNickname(
            @RequestParam @Valid @Size(min = 2, max = 10, message = "닉네임은 2자 이상 10자 이하여야 합니다.")
            @Pattern(regexp = "^[a-zA-Z0-9가-힣_]+$", message = "닉네임은 영문, 숫자, 한글, 밑줄만 사용 가능합니다.")
            String nickname
    ) {
        return ResponseEntity.ok(
                SuccessResponse.of(SuccessCode.SUCCESS_NICKNAME_CHECK, userQueryFacade.checkNickname(nickname))
        );
    }

    @Override
    @GetMapping("/me")
    public ResponseEntity<SuccessResponse<UserProfileRes>> getMyProfile(
            @CurrentUser Long userId
    ) {
        return ResponseEntity.ok(
                SuccessResponse.of(SuccessCode.SUCCESS_FETCH, userQueryFacade.getUserProfile(userId))
        );
    }

    @Override
    @GetMapping("/me/keywords")
    public ResponseEntity<SuccessResponse<UserKeywordsRes>> getMyKeywords(
            @CurrentUser Long userId
    ) {
        return ResponseEntity.ok(
                SuccessResponse.of(SuccessCode.SUCCESS_KEYWORDS_FETCH, userQueryFacade.getUserKeywords(userId))
        );
    }

    @Override
    @GetMapping("/me/collections")
    public ResponseEntity<SuccessResponse<UserCollectionsRes>> getMyCollections(
            @CurrentUser Long userId
    ) {
        return ResponseEntity.ok(
                SuccessResponse.of(SuccessCode.SUCCESS_COLLECTIONS_FETCH, userQueryFacade.getMyCollections(userId))
        );
    }

    @Override
    @GetMapping("/me/bookmarked-collections")
    public ResponseEntity<SuccessResponse<UserBookmarkedCollectionsRes>> getMyBookmarkedCollections(
            @CurrentUser Long userId
    ) {
        return ResponseEntity.ok(
                SuccessResponse.of(SuccessCode.SUCCESS_COLLECTIONS_FETCH, userQueryFacade.getMyBookmarkedCollections(userId))
        );
    }

    @Override
    @GetMapping("/{userId}")
    public ResponseEntity<SuccessResponse<UserProfileRes>> getUserProfile(
            @PathVariable Long userId
    ) {
        return ResponseEntity.ok(
                SuccessResponse.of(SuccessCode.SUCCESS_FETCH, userQueryFacade.getUserProfile(userId))
        );
    }

    @Override
    @GetMapping("/{userId}/keywords")
    public ResponseEntity<SuccessResponse<UserKeywordsRes>> getUserKeywords(
            @PathVariable Long userId
    ) {
        return ResponseEntity.ok(
                SuccessResponse.of(SuccessCode.SUCCESS_KEYWORDS_FETCH, userQueryFacade.getUserKeywords(userId))
        );
    }

    @Override
    @GetMapping("/{userId}/collections")
    public ResponseEntity<SuccessResponse<UserCollectionsRes>> getUserCollections(
            @PathVariable Long userId
    ) {
        return ResponseEntity.ok(
                SuccessResponse.of(SuccessCode.SUCCESS_COLLECTIONS_FETCH, userQueryFacade.getPublicCollections(userId))
        );
    }

    @Override
    @GetMapping("/{userId}/bookmarked-collections")
    public ResponseEntity<SuccessResponse<UserBookmarkedCollectionsRes>> getUserBookmarkedCollections(
            @PathVariable Long userId
    ) {
        return ResponseEntity.ok(
                SuccessResponse.of(SuccessCode.SUCCESS_COLLECTIONS_FETCH, userQueryFacade.getPublicBookmarkedCollections(userId))
        );
    }

    @Override
    @PatchMapping("/me/keywords/recalculate")
    public ResponseEntity<SuccessResponse<Void>> recalculateKeyword(
            @CurrentUser Long userId
    ) {
        userCommandFacade.callGpt(userId);
        return ResponseEntity.ok(SuccessResponse.of(SuccessCode.SUCCESS_KEYWORDS_FETCH));
    }

}
