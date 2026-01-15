package kr.flint.api.domain.auth.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.flint.api.domain.auth.dto.request.RefreshTokenReq;
import kr.flint.api.domain.auth.dto.request.SignupReq;
import kr.flint.api.domain.auth.dto.request.SocialVerifyReq;
import kr.flint.api.domain.auth.dto.response.AuthTokenRes;
import kr.flint.api.domain.auth.dto.response.SocialVerifyRes;
import kr.flint.api.domain.auth.event.UserSignedUpEvent;
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
import kr.flint.bookmark.service.BookmarkService;
import kr.flint.ott.service.OttService;
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
    private final BookmarkService bookmarkService;
    private final OttService ottService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 소셜 로그인
     */
    @Transactional
    public SocialVerifyRes verifySocialCode(SocialVerifyReq request) {
        SocialUserInfo userInfo = getSocialUserInfo(request.provider(), request.code());
        SocialVerifyResult result = authService.verifySocialUser(request.provider(), userInfo);

        if (result.isRegistered()) {
            // 기존 회원 - 인증 정보 조회 후 토큰 발급
            UserAuthInfo authInfo = userService.getAuthInfo(result.userId());
            AuthTokens tokens = authService.issueTokens(authInfo.userId(), authInfo.role());
            return SocialVerifyRes.registered(tokens.accessToken(), tokens.refreshToken(), authInfo.userId());
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

        UserAuthInfo authInfo = userService.create(request.nickname());
        userIdentityService.create(authInfo.userId(), payload.provider(), payload.providerUserId());

        bookmarkService.createContentBookmarks(authInfo.userId(), request.favoriteContentIds());
        ottService.createUserOtts(authInfo.userId(), request.subscribedOttIds());

        // 비동기 취향 분석 이벤트 발행 (트랜잭션 커밋 후 처리)
        eventPublisher.publishEvent(UserSignedUpEvent.of(authInfo.userId(), request.favoriteContentIds()));

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

    // 소셜 제공자별 사용자 정보 조회
    private SocialUserInfo getSocialUserInfo(AuthProvider provider, String code) {
        // 일단 mvp에서는 kakao만 제공
        if (provider != AuthProvider.KAKAO) {
            throw new AuthException(AuthErrorCode.UNSUPPORTED_PROVIDER);
        }
        return kakaoOAuthClient.getUserInfoByCode(code);
    }
}
