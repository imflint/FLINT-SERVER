package kr.flint.api.domain.auth.service;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.flint.api.domain.auth.dto.request.RefreshTokenReq;
import kr.flint.api.domain.auth.dto.request.SignupReq;
import kr.flint.api.domain.auth.dto.request.SocialVerifyReq;
import kr.flint.api.domain.auth.dto.response.AuthTokenRes;
import kr.flint.api.domain.auth.dto.response.SocialVerifyRes;
import kr.flint.api.domain.auth.event.UserSignedUpEvent;
import kr.flint.api.global.oauth.client.AppleOAuthClient;
import kr.flint.api.global.oauth.client.KakaoOAuthClient;
import kr.flint.auth.dto.AuthTokens;
import kr.flint.auth.dto.SocialUserInfo;
import kr.flint.auth.dto.SocialVerifyResult;
import kr.flint.auth.dto.TempTokenPayload;
import kr.flint.auth.enums.AuthProvider;
import kr.flint.auth.exception.AuthErrorCode;
import kr.flint.auth.exception.AuthException;
import kr.flint.auth.service.AuthService;
import kr.flint.auth.service.UserIdentityService;
import kr.flint.bookmark.service.BookmarkCommandService;
import kr.flint.collection.service.CollectionService;
import kr.flint.content.service.ContentService;
import kr.flint.ott.service.OttService;
import kr.flint.taste.service.TasteService;
import kr.flint.terms.domain.TermsContext;
import kr.flint.terms.service.TermsService;
import kr.flint.user.dto.response.UserAuthInfo;
import kr.flint.user.service.UserService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthFacade {

    private final AuthService authService;
    private final UserService userService;
    private final UserIdentityService userIdentityService;
    private final KakaoOAuthClient kakaoOAuthClient;
    private final AppleOAuthClient appleOAuthClient;
    private final BookmarkCommandService bookmarkCommandService;
    private final OttService ottService;
    private final ApplicationEventPublisher eventPublisher;
	private final CollectionService collectionService;
	private final TasteService tasteService;
	private final ContentService contentService;
	private final TermsService termsService;

	/**
     * 소셜 로그인
     */
    @Transactional
    public SocialVerifyRes verifySocialCode(SocialVerifyReq request) {
        SocialUserInfo userInfo = getSocialUserInfo(request);
        SocialVerifyResult result = authService.verifySocialUser(request.provider(), userInfo);

        if (result.isRegistered()) {
            // 기존 회원 - 인증 정보 조회 후 토큰 발급
            UserAuthInfo authInfo = userService.getAuthInfo(result.userId());
            AuthTokens tokens = authService.issueTokens(authInfo.userId(), authInfo.role());
            return SocialVerifyRes.registered(tokens.accessToken(), tokens.refreshToken(), authInfo.userId(),
				authInfo.nickname());
        }

        // 신규 회원
        return SocialVerifyRes.unregistered(result.tempToken());
    }

    /**
     * 회원가입
     */
    @Transactional
    public AuthTokenRes signup(SignupReq request) {
        TempTokenPayload payload = authService.verifyTempToken(request.tempToken());
        List<Long> agreedTermsIds = request.agreedTermsIdValues();
        List<Long> favoriteContentIds = request.favoriteContentIdValues();

        UserAuthInfo authInfo = userService.create(request.nickname(), request.profileImage());
        userIdentityService.create(authInfo.userId(), payload.provider(), payload.providerUserId());
        termsService.validateAndCreateAgreements(authInfo.userId(), TermsContext.SIGNUP, agreedTermsIds);

        bookmarkCommandService.createContentBookmarks(authInfo.userId(), favoriteContentIds);
        favoriteContentIds.forEach(contentService::incContentBookmarkCount);

        // 비동기 취향 분석 이벤트 발행 (트랜잭션 커밋 후 처리)
        eventPublisher.publishEvent(UserSignedUpEvent.of(authInfo.userId(), favoriteContentIds));

        // 토큰 발급
        AuthTokens tokens = authService.issueTokens(authInfo.userId(), authInfo.role());
        return AuthTokenRes.from(tokens);
    }

    /**
     * 토큰 갱신
     */
    @Transactional
    public AuthTokenRes refreshTokens(RefreshTokenReq request) {
        // 토큰 검증 및 Rotation
        Long userId = authService.validateAndRotateToken(request.refreshToken());

        // 인증 정보 조회 후 새 토큰 발급
        UserAuthInfo authInfo = userService.getAuthInfo(userId);
        AuthTokens tokens = authService.issueTokens(authInfo.userId(), authInfo.role());
        return AuthTokenRes.from(tokens);
    }

    /**
     * 현재 세션 로그아웃
     */
    public void logout(String accessToken, String refreshToken) {
        authService.logout(accessToken, refreshToken);
    }

    /**
     * 모든 세션 로그아웃
     */
    public void logoutAll(Long userId, String accessToken) {
        authService.logoutAll(userId, accessToken);
    }

    /**
     * 회원탈퇴
     */
    @Transactional
    public void withdraw(Long userId, String accessToken, List<Long> agreedTermsIds) {
        termsService.validateAndCreateAgreements(userId, TermsContext.WITHDRAWAL, agreedTermsIds);

		//User 삭제
		userService.deleteUser(userId);

		//토큰 삭제
        authService.withdraw(userId, accessToken);

		//컬렉션 삭제
		collectionService.deleteCollectionByUser(userId);

		//북마크 삭제
		bookmarkCommandService.deleteBookmarkByUser(userId);

		//취향 키워드 삭제
		tasteService.deleteUserKeywords(userId);

		//ott 삭제
		ottService.deleteUserOtts(userId);
    }

    /**
     * 개발용 로그인 (dev/local 환경에서만 사용)
     */
    @Transactional
    public AuthTokenRes devLogin(Long userId) {
        UserAuthInfo authInfo = userService.getAuthInfo(userId);
        AuthTokens tokens = authService.issueTokens(authInfo.userId(), authInfo.role());
        return AuthTokenRes.from(tokens);
    }

    // 소셜 제공자별 사용자 정보 조회
    // accessToken -> Mobile (토큰으로 직접 조회), code -> Web (코드→토큰 교환→조회)
    private SocialUserInfo getSocialUserInfo(SocialVerifyReq request) {
        return switch (request.provider()) {
            case KAKAO -> request.hasAccessToken()
                    ? kakaoOAuthClient.getUserInfoByAccessToken(request.accessToken())
                    : kakaoOAuthClient.getUserInfoByCode(request.code());
            case APPLE -> {
                if (!request.hasAccessToken()) {
                    throw new AuthException(AuthErrorCode.UNSUPPORTED_SOCIAL_FLOW);
                }
                yield appleOAuthClient.getUserInfoByIdentityToken(request.accessToken());
            }
        };
    }
}
