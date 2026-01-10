package kr.flint.api.auth.service;

import kr.flint.auth.dto.request.RefreshTokenRequest;
import kr.flint.auth.dto.request.SignupRequest;
import kr.flint.auth.dto.request.SocialVerifyRequest;
import kr.flint.auth.dto.response.AuthTokenResponse;
import kr.flint.auth.dto.response.SocialVerifyResponse;
import kr.flint.auth.service.AuthService;
import kr.flint.auth.service.AuthService.SocialVerifyResult;
import kr.flint.auth.service.AuthService.TempTokenPayload;
import kr.flint.auth.service.UserIdentityService;
import kr.flint.user.dto.response.UserAuthInfo;
import kr.flint.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthFacade {

    private final AuthService authService;
    private final UserService userService;
    private final UserIdentityService userIdentityService;

    /**
     * 소셜 로그인
     */
    @Transactional
    public SocialVerifyResponse verifySocialCode(SocialVerifyRequest request) {
        SocialVerifyResult result = authService.verifySocialCode(request.provider(), request.code());

        if (result.isRegistered()) {
            // 기존 회원 - 인증 정보 조회 후 토큰 발급
            UserAuthInfo authInfo = userService.getAuthInfo(result.userId());
            AuthTokenResponse tokens = authService.issueTokens(authInfo.userId(), authInfo.role());
            return SocialVerifyResponse.registered(tokens.accessToken(), tokens.refreshToken(), authInfo.userId());
        }

        // 신규 회원
        return SocialVerifyResponse.unregistered(result.tempToken(), result.email());
    }

    /**
     * 회원가입
     */
    @Transactional
    public AuthTokenResponse signup(SignupRequest request) {
        TempTokenPayload payload = authService.verifyTempToken(request.tempToken());

        UserAuthInfo authInfo = userService.create(request.nickname());
        userIdentityService.create(authInfo.userId(), payload.provider(), payload.providerUserId());

        // 좋아하는 작품 북마크 생성
        // TODO: ContentBookmarkService 연동 (modules:bookmark)

        // 구독 OTT 생성
        // TODO: UserOttService 연동 (modules:ott)

        // 토큰 발급
        return authService.issueTokens(authInfo.userId(), authInfo.role());
    }

    /**
     * 토큰 갱신
     */
    @Transactional
    public AuthTokenResponse refreshTokens(RefreshTokenRequest request) {
        // 토큰 검증 및 Rotation
        Long userId = authService.validateAndRotateToken(request.refreshToken());

        // 인증 정보 조회 후 새 토큰 발급
        UserAuthInfo authInfo = userService.getAuthInfo(userId);
        return authService.issueTokens(authInfo.userId(), authInfo.role());
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
}
