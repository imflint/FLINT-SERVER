package kr.flint.api.auth.service;

import kr.flint.auth.dto.request.RefreshTokenRequest;
import kr.flint.auth.dto.request.SignupRequest;
import kr.flint.auth.dto.request.SocialVerifyRequest;
import kr.flint.auth.dto.response.AuthTokenResponse;
import kr.flint.auth.dto.response.NicknameCheckResponse;
import kr.flint.auth.dto.response.SocialVerifyResponse;
import kr.flint.auth.service.AuthService;
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

    // 소셜 로그인
    public SocialVerifyResponse verifySocialCode(SocialVerifyRequest request) {
        return authService.verifySocialCode(request.provider(), request.code());
    }

    // 닉네임 중복 체크
    public NicknameCheckResponse checkNickname(String nickname) {
        boolean exists = userService.existsByNickname(nickname);
        return NicknameCheckResponse.of(!exists);
    }

    // 회원가입
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

    // 토큰 갱신
    public AuthTokenResponse refreshTokens(RefreshTokenRequest request) {
        return authService.refreshTokens(request.refreshToken());
    }

    // 로그아웃 (refreshToken이 null이면 전체 로그아웃)
    public void logout(Long userId, String accessToken, String refreshToken) {
        if (refreshToken == null) {
            authService.logoutAll(userId, accessToken);
        } else {
            authService.logout(accessToken, refreshToken);
        }
    }
}
