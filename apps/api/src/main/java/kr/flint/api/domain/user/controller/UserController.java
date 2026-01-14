package kr.flint.api.domain.user.controller;

import jakarta.validation.Valid;
import kr.flint.api.domain.user.controller.spec.UserControllerDocs;
import kr.flint.api.domain.user.dto.request.NicknameCheckReq;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserQueryFacade userQueryFacade;
	private final UserCommandFacade userCommandFacade;

   // @Override
    @GetMapping("/nickname/check")
    public ResponseEntity<SuccessResponse<NicknameCheckResponse>> checkNickname(
            @Valid @ModelAttribute NicknameCheckReq request
    ) {
        return ResponseEntity.ok(
                SuccessResponse.of(SuccessCode.SUCCESS_NICKNAME_CHECK, userQueryFacade.checkNickname(request.nickname()))
        );
    }

    //@Override
    @GetMapping("/{userId}")
    public ResponseEntity<SuccessResponse<UserProfileRes>> getUserProfile(
            @PathVariable Long userId
    ) {
        return ResponseEntity.ok(
                SuccessResponse.of(SuccessCode.SUCCESS_FETCH, userQueryFacade.getUserProfile(userId))
        );
    }

    //@Override
    @GetMapping("/{userId}/keywords")
    public ResponseEntity<SuccessResponse<UserKeywordsRes>> getUserKeywords(
            @PathVariable Long userId
    ) {
        return ResponseEntity.ok(
                SuccessResponse.of(SuccessCode.SUCCESS_KEYWORDS_FETCH, userQueryFacade.getUserKeywords(userId))
        );
    }

    //@Override
    @GetMapping("/{userId}/collections")
    public ResponseEntity<SuccessResponse<UserCollectionsRes>> getUserCollections(
            @CurrentUser(required = false) Long currentUserId,
            @PathVariable Long userId
    ) {
        boolean isOwner = isOwner(currentUserId, userId);
        return ResponseEntity.ok(
                SuccessResponse.of(SuccessCode.SUCCESS_COLLECTIONS_FETCH, userQueryFacade.getUserCollections(userId, isOwner))
        );
    }

    //@Override
    @GetMapping("/{userId}/bookmarked-collections")
    public ResponseEntity<SuccessResponse<UserBookmarkedCollectionsRes>> getUserBookmarkedCollections(
            @CurrentUser(required = false) Long currentUserId,
            @PathVariable Long userId
    ) {
        boolean isOwner = isOwner(currentUserId, userId);
        return ResponseEntity.ok(
                SuccessResponse.of(SuccessCode.SUCCESS_COLLECTIONS_FETCH, userQueryFacade.getUserBookmarkedCollections(userId, isOwner))
        );
    }

	@PatchMapping("/recalculate/keyword")
	public ResponseEntity<SuccessResponse<Void>> recalculateKeyword(
		@CurrentUser(required = true) Long userId
	){
		userCommandFacade.callGpt(userId);
		return ResponseEntity.ok(SuccessResponse.of(SuccessCode.SUCCESS_KEYWORDS_FETCH));
	}

    // 본인 여부 확인 (비로그인 시 false)
    private boolean isOwner(Long currentUserId, Long userId) {
        return currentUserId != null && currentUserId.equals(userId);
    }


}
