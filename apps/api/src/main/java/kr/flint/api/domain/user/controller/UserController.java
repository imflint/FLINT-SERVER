package kr.flint.api.domain.user.controller;

import kr.flint.api.domain.user.controller.spec.UserControllerDocs;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Validated
public class UserController implements UserControllerDocs {

    private final UserQueryFacade userQueryFacade;
	private final UserCommandFacade userCommandFacade;

    @Override
    @GetMapping("/nickname/check")
    public ResponseEntity<SuccessResponse<NicknameCheckResponse>> checkNickname(
            @RequestParam String nickname
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
    public ResponseEntity<SuccessResponse<UserCollectionsRes>> getMyBookmarkedCollections(
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
            @CurrentUser Long currentUserId,
            @PathVariable Long userId
    ) {
        return ResponseEntity.ok(
                SuccessResponse.of(SuccessCode.SUCCESS_COLLECTIONS_FETCH, userQueryFacade.getPublicCollections(currentUserId, userId))
        );
    }

    @Override
    @GetMapping("/{userId}/bookmarked-collections")
    public ResponseEntity<SuccessResponse<UserCollectionsRes>> getUserBookmarkedCollections(
            @CurrentUser Long currentUserId,
            @PathVariable Long userId
    ) {
        return ResponseEntity.ok(
                SuccessResponse.of(SuccessCode.SUCCESS_COLLECTIONS_FETCH, userQueryFacade.getPublicBookmarkedCollections(currentUserId, userId))
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
