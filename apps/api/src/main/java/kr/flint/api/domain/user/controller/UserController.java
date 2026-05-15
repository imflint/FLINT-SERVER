package kr.flint.api.domain.user.controller;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import kr.flint.api.domain.content.dto.GetContentDetailRes;
import kr.flint.api.domain.content.dto.GetContentListRes;
import kr.flint.api.domain.content.service.ContentQueryFacade;
import kr.flint.api.domain.user.controller.spec.UserControllerDocs;
import kr.flint.api.domain.user.dto.request.UpdateProfileImageReq;
import kr.flint.api.domain.user.dto.request.UpdateNicknameReq;
import kr.flint.api.domain.user.dto.response.MyProfileRes;
import kr.flint.api.domain.user.dto.response.UserCollectionsRes;
import kr.flint.api.domain.user.dto.response.UserKeywordsRes;
import kr.flint.api.domain.user.dto.response.UserProfileRes;
import kr.flint.api.domain.user.service.UserCommandFacade;
import kr.flint.api.domain.user.service.UserQueryFacade;
import kr.flint.api.global.security.annotation.CurrentUser;
import kr.flint.shared.dto.response.SuccessCode;
import kr.flint.shared.dto.response.SuccessResponse;
import kr.flint.user.domain.NicknamePolicy;
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
	private final ContentQueryFacade contentQueryFacade;

    @Override
    @GetMapping("/nickname/check")
    public ResponseEntity<SuccessResponse<NicknameCheckResponse>> checkNickname(
            @RequestParam
            @NotBlank(message = NicknamePolicy.MESSAGE)
            @Size(min = NicknamePolicy.MIN_LENGTH, max = NicknamePolicy.MAX_LENGTH, message = NicknamePolicy.MESSAGE)
            @Pattern(regexp = NicknamePolicy.REGEX, message = NicknamePolicy.MESSAGE)
            String nickname
    ) {
        return ResponseEntity.ok(
                SuccessResponse.of(SuccessCode.SUCCESS_NICKNAME_CHECK, userQueryFacade.checkNickname(nickname))
        );
    }

    @Override
    @GetMapping("/me")
    public ResponseEntity<SuccessResponse<MyProfileRes>> getMyProfile(
            @CurrentUser Long userId
    ) {
        return ResponseEntity.ok(
                SuccessResponse.of(SuccessCode.SUCCESS_FETCH, userQueryFacade.getMyProfile(userId))
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
    @PutMapping("/me/nickname")
    public ResponseEntity<SuccessResponse<Void>> updateNickname(
            @CurrentUser Long userId,
            @Valid @RequestBody UpdateNicknameReq request
    ) {
        userCommandFacade.updateNickname(userId, request.nickname());
        return ResponseEntity.ok(SuccessResponse.of(SuccessCode.SUCCESS_UPDATE));
    }

    @Override
    @PatchMapping("/me/keywords/recalculate")
    public ResponseEntity<SuccessResponse<Void>> recalculateKeyword(
            @CurrentUser Long userId
    ) {
        userCommandFacade.recalculateKeywordOnDemand(userId);
        return ResponseEntity.ok(SuccessResponse.of(SuccessCode.SUCCESS_KEYWORDS_FETCH));
    }


    @Override
    @PutMapping("/me/profile-image")
    public ResponseEntity<SuccessResponse<Void>> updateProfileImage(
            @CurrentUser Long userId,
            @Valid @RequestBody UpdateProfileImageReq request
    ) {
        userCommandFacade.updateProfileImage(userId, request.profileImage());
        return ResponseEntity.ok(SuccessResponse.of(SuccessCode.SUCCESS_UPDATE));
    }

	@Override
	@GetMapping("/{userId}/bookmarked-contents")
	public ResponseEntity<SuccessResponse<GetContentListRes>> getUserBookmarkedContents(
		@PathVariable Long userId
	){
		List<GetContentDetailRes> getContentDetailResList = contentQueryFacade.getContentDetailList(userId);
		return ResponseEntity.ok(SuccessResponse.of(SuccessCode.SUCCESS_FETCH, new GetContentListRes(getContentDetailResList)));
	}

}
