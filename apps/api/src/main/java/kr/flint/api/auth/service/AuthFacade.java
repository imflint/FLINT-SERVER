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
import kr.flint.user.domain.User;
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
            // 기존 회원 - User 조회 후 토큰 발급
            User user = userService.getById(result.userId());
            AuthTokenResponse tokens = authService.issueTokens(user.getId(), user.getUserRole().name());
            return SocialVerifyResponse.registered(tokens.accessToken(), tokens.refreshToken(), user.getId());
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

        User user = User.createFling(request.nickname());
        User savedUser = userService.create(user);
        userIdentityService.create(savedUser.getId(), payload.provider(), payload.providerUserId());

        // 좋아하는 작품 북마크 생성
        // TODO: ContentBookmarkService 연동 (modules:bookmark)

        // 구독 OTT 생성
        // TODO: UserOttService 연동 (modules:ott)

        // 토큰 발급
        return authService.issueTokens(savedUser.getId(), savedUser.getUserRole().name());
    }

    /**
     * 토큰 갱신
     */
    @Transactional
    public AuthTokenResponse refreshTokens(RefreshTokenRequest request) {
        // 토큰 검증 및 Rotation
        Long userId = authService.validateAndRotateToken(request.refreshToken());

        // User 조회 후 새 토큰 발급
        User user = userService.getById(userId);
        return authService.issueTokens(user.getId(), user.getUserRole().name());
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
