package kr.flint.auth.service;

import kr.flint.auth.client.KakaoOAuthClient;
import kr.flint.auth.client.dto.KakaoUserInfo;
import kr.flint.auth.domain.RefreshTokenValue;
import kr.flint.auth.domain.UserIdentity;
import kr.flint.auth.domain.enums.AuthProvider;
import kr.flint.auth.domain.enums.RefreshTokenStatus;
import kr.flint.auth.dto.response.AuthTokenResponse;
import kr.flint.auth.dto.response.SocialVerifyResult;
import kr.flint.auth.dto.response.TempTokenPayload;
import kr.flint.auth.exception.AuthErrorCode;
import kr.flint.auth.jwt.JwtProvider;
import kr.flint.auth.jwt.AccessTokenBlacklist;
import kr.flint.auth.repository.RefreshTokenRepository;
import kr.flint.auth.exception.AuthException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccessTokenBlacklist accessTokenBlacklist;
    private final UserIdentityService userIdentityService;
    private final KakaoOAuthClient kakaoOAuthClient;

    // Authorization Code로 소셜 로그인 처리 (Facade용 - 토큰 발급 분리)
    public SocialVerifyResult verifySocialCode(AuthProvider provider, String code) {
        KakaoUserInfo userInfo = getSocialUserInfoByCode(provider, code);

        Optional<UserIdentity> existingIdentity = userIdentityService
                .findByProviderAndProviderUserId(provider, userInfo.providerUserId());

        if (existingIdentity.isPresent()) {
            // 기존 회원 - userId 반환 (토큰 발급은 Facade에서)
            return SocialVerifyResult.registered(existingIdentity.get().getUserId());
        }

        // 신규 회원 - 임시 토큰 발급
        String tempToken = jwtProvider.createTempToken(provider, userInfo.providerUserId());
        return SocialVerifyResult.unregistered(tempToken);
    }

    // Temp Token 검증 및 정보 추출
    public TempTokenPayload verifyTempToken(String tempToken) {
        if (!jwtProvider.isTempToken(tempToken)) {
            throw new AuthException(AuthErrorCode.INVALID_TOKEN);
        }

        AuthProvider provider = jwtProvider.getProvider(tempToken);
        String providerUserId = jwtProvider.getProviderUserId(tempToken);

        return new TempTokenPayload(provider, providerUserId);
    }

    // Access / Refresh Token 발급
    @Transactional
    public AuthTokenResponse issueTokens(Long userId, String role) {
        String accessToken = jwtProvider.createAccessToken(userId, role);
        String refreshToken = refreshTokenRepository.createToken();

        // Redis에 Refresh Token 저장 (token → userId)
        long ttlSeconds = jwtProvider.getRefreshTokenTtlSeconds();
        refreshTokenRepository.save(refreshToken, userId, ttlSeconds);

        return AuthTokenResponse.of(accessToken, refreshToken, userId);
    }

    // Refresh Token 검증 및 Rotation
    @Transactional
    public Long validateAndRotateToken(String refreshToken) {
        if (!refreshTokenRepository.tryLock(refreshToken)) {
            throw new AuthException(AuthErrorCode.CONCURRENT_REFRESH_REQUEST);
        }

        try {
            // 토큰 조회 및 상태 확인
            RefreshTokenValue tokenValue = refreshTokenRepository.findByToken(refreshToken)
                    .orElseThrow(() -> new AuthException(AuthErrorCode.REFRESH_TOKEN_NOT_FOUND));

            switch (tokenValue.status()) {
                case USED -> {
                    log.warn("토큰 탈취 감지 userId: {}", tokenValue.userId());
                    refreshTokenRepository.revokeAllByUserId(tokenValue.userId());
                    throw new AuthException(AuthErrorCode.REFRESH_TOKEN_REUSED);
                }
                case REVOKED -> throw new AuthException(AuthErrorCode.REFRESH_TOKEN_REVOKED);
                case VALID -> {}
            }

            if (tokenValue.isExpired()) {
                throw new AuthException(AuthErrorCode.EXPIRED_TOKEN);
            }

            // 기존 토큰 USED로 변경
            refreshTokenRepository.updateStatus(refreshToken, RefreshTokenStatus.USED);

            return tokenValue.userId();
        } finally {
            refreshTokenRepository.unlock(refreshToken);
        }
    }

    // 로그아웃 (Blacklist + RTR)
    @Transactional
    public void logout(String accessToken, String refreshToken) {
        if (accessToken != null) {
            long remainingTtl = jwtProvider.getRemainingTtlSeconds(accessToken);
            if (remainingTtl > 0) {
                accessTokenBlacklist.blacklist(accessToken, remainingTtl);
            }
        }

        // Refresh Token REVOKED로 변경 및 삭제
        if (refreshToken != null) {
            refreshTokenRepository.findByToken(refreshToken)
                    .ifPresent(value -> {
                        refreshTokenRepository.updateStatus(refreshToken, RefreshTokenStatus.REVOKED);
                        refreshTokenRepository.delete(refreshToken, value.userId());
                    });
        }
    }

    // 전체 로그아웃 (모든 Refresh Token 무효화)
    @Transactional
    public void logoutAll(Long userId, String accessToken) {
        // Access Token Blacklist 추가
        if (accessToken != null) {
            long remainingTtl = jwtProvider.getRemainingTtlSeconds(accessToken);
            if (remainingTtl > 0) {
                accessTokenBlacklist.blacklist(accessToken, remainingTtl);
            }
        }

        // 모든 Refresh Token 삭제
        refreshTokenRepository.deleteAllByUserId(userId);
    }

    // 소셜 제공자별 사용자 정보 조회
    private KakaoUserInfo getSocialUserInfoByCode(AuthProvider provider, String code) {
        return switch (provider) {
            case KAKAO -> kakaoOAuthClient.getUserInfoByCode(code);
            case APPLE -> throw new AuthException(AuthErrorCode.UNSUPPORTED_PROVIDER);
        };
    }
}
