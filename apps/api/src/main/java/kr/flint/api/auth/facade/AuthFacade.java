package kr.flint.api.auth.facade;

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
import kr.flint.user.domain.UserRole;
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

    // 소셜 로그인 (Authorization Code Flow)
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
        // 1. Temp Token 검증
        TempTokenPayload payload = authService.verifyTempToken(request.tempToken());

        // 2. User 생성
        UserRole userRole = UserRole.valueOf(request.userRole());
        User user = createUser(request.realName(), request.nickname(), userRole);
        User savedUser = userService.create(user);

        // 3. UserIdentity 생성
        userIdentityService.create(savedUser.getId(), payload.provider(), payload.providerUserId());

        // 4. 좋아하는 작품 북마크 생성
        // TODO: ContentBookmarkService 연동 (modules:bookmark)
        // if (request.favoriteContentIds() != null && !request.favoriteContentIds().isEmpty()) {
        //     contentBookmarkService.createBulk(savedUser.getId(), request.favoriteContentIds());
        // }

        // 5. 구독 OTT 생성
        // TODO: UserOttService 연동 (modules:ott)
        // if (request.subscribedOttIds() != null && !request.subscribedOttIds().isEmpty()) {
        //     userOttService.createBulk(savedUser.getId(), request.subscribedOttIds());
        // }

        // 6. 토큰 발급
        return authService.issueTokens(savedUser.getId(), userRole.name());
    }

    // 토큰 갱신
    public AuthTokenResponse refreshTokens(RefreshTokenRequest request) {
        return authService.refreshTokens(request.refreshToken());
    }

    // 로그아웃 (refreshToken이 null이면 전체 로그아웃)
    public void logout(Long userId, String refreshToken) {
        if (refreshToken == null) {
            authService.logoutAll(userId);
        } else {
            authService.logout(refreshToken);
        }
    }

    private User createUser(String realName, String nickname, UserRole userRole) {
        return switch (userRole) {
            case FLING -> User.createFling(realName, nickname);
            case FLINER -> User.createFliner(realName, nickname);
            case ADMIN -> throw new IllegalArgumentException("관리자 계정은 직접 생성할 수 없습니다.");
        };
    }
}
