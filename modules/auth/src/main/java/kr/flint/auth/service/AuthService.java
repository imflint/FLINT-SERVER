package kr.flint.auth.service;

import kr.flint.auth.client.KakaoOAuthClient;
import kr.flint.auth.client.dto.KakaoUserInfo;
import kr.flint.auth.domain.RefreshTokenValue;
import kr.flint.auth.domain.UserIdentity;
import kr.flint.auth.domain.enums.AuthProvider;
import kr.flint.auth.domain.enums.RefreshTokenStatus;
import kr.flint.auth.dto.response.AuthTokenResponse;
import kr.flint.auth.dto.response.SocialVerifyResponse;
import kr.flint.auth.exception.AuthErrorCode;
import kr.flint.auth.repository.RefreshTokenRepository;
import kr.flint.shared.exception.GeneralException;
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
    private final TokenBlacklistService tokenBlacklistService;
    private final UserIdentityService userIdentityService;
    private final KakaoOAuthClient kakaoOAuthClient;

    // Authorization Code로 소셜 로그인 처리
    public SocialVerifyResponse verifySocialCode(AuthProvider provider, String code) {
        KakaoUserInfo userInfo = getSocialUserInfoByCode(provider, code);

        Optional<UserIdentity> existingIdentity = userIdentityService
                .findByProviderAndProviderUserId(provider, userInfo.providerUserId());

        if (existingIdentity.isPresent()) {
            // 기존 회원 - 토큰 발급
            UserIdentity identity = existingIdentity.get();
            AuthTokenResponse tokens = issueTokens(identity.getUserId(), null);
            return SocialVerifyResponse.registered(
                    tokens.accessToken(),
                    tokens.refreshToken(),
                    identity.getUserId()
            );
        }

        // 신규 회원 - 임시 토큰 발급
        String tempToken = jwtProvider.createTempToken(provider, userInfo.providerUserId());
        return SocialVerifyResponse.unregistered(tempToken, userInfo.email());
    }

    // Temp Token 검증 및 정보 추출
    public TempTokenPayload verifyTempToken(String tempToken) {
        if (!jwtProvider.isTempToken(tempToken)) {
            throw new GeneralException(AuthErrorCode.INVALID_TOKEN);
        }

        AuthProvider provider = jwtProvider.getProvider(tempToken);
        String providerUserId = jwtProvider.getProviderUserId(tempToken);

        return new TempTokenPayload(provider, providerUserId);
    }

    // Access / Refresh Token 발급
    @Transactional
    public AuthTokenResponse issueTokens(Long userId, String role) {
        String accessToken = jwtProvider.createAccessToken(userId, role);
        String refreshToken = jwtProvider.createRefreshToken();

        // Redis에 Refresh Token 저장 (token → userId)
        long ttlSeconds = jwtProvider.getRefreshTokenTtlSeconds();
        refreshTokenRepository.save(refreshToken, userId, ttlSeconds);

        return AuthTokenResponse.of(accessToken, refreshToken, userId);
    }

    // Refresh Token으로 토큰 갱신 (RTR 적용)
    @Transactional
    public AuthTokenResponse refreshTokens(String refreshToken) {
        // 1. 락 획득 (동시 요청 방지)
        if (!refreshTokenRepository.tryLock(refreshToken)) {
            throw new GeneralException(AuthErrorCode.CONCURRENT_REFRESH_REQUEST);
        }

        try {
            // 2. 토큰 조회 및 상태 확인
            RefreshTokenValue tokenValue = refreshTokenRepository.findByToken(refreshToken)
                    .orElseThrow(() -> new GeneralException(AuthErrorCode.REFRESH_TOKEN_NOT_FOUND));

            // 3. 상태별 처리
            switch (tokenValue.status()) {
                case USED -> {
                    // 토큰 재사용 감지! 보안 위협 → 전체 무효화
                    log.warn("Refresh token reuse detected for userId: {}", tokenValue.userId());
                    refreshTokenRepository.revokeAllByUserId(tokenValue.userId());
                    throw new GeneralException(AuthErrorCode.REFRESH_TOKEN_REUSED);
                }
                case REVOKED -> throw new GeneralException(AuthErrorCode.REFRESH_TOKEN_REVOKED);
                case VALID -> { /* 계속 진행 */ }
            }

            // 4. 만료 확인
            if (tokenValue.isExpired()) {
                throw new GeneralException(AuthErrorCode.EXPIRED_TOKEN);
            }

            // 5. 기존 토큰 USED로 변경
            refreshTokenRepository.updateStatus(refreshToken, RefreshTokenStatus.USED);

            // 6. 새 토큰 발급
            return issueTokens(tokenValue.userId(), null);
        } finally {
            // 7. 락 해제
            refreshTokenRepository.unlock(refreshToken);
        }
    }

    // 로그아웃 (Blacklist + RTR 적용)
    @Transactional
    public void logout(String accessToken, String refreshToken) {
        // 1. Access Token Blacklist 추가
        if (accessToken != null) {
            long remainingTtl = jwtProvider.getRemainingTtlSeconds(accessToken);
            if (remainingTtl > 0) {
                tokenBlacklistService.blacklist(accessToken, remainingTtl);
            }
        }

        // 2. Refresh Token REVOKED로 변경 및 삭제
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
                tokenBlacklistService.blacklist(accessToken, remainingTtl);
            }
        }

        // 모든 Refresh Token 삭제
        refreshTokenRepository.deleteAllByUserId(userId);
    }

    // 소셜 제공자별 사용자 정보 조회 (Authorization Code 사용)
    private KakaoUserInfo getSocialUserInfoByCode(AuthProvider provider, String code) {
        return switch (provider) {
            case KAKAO -> kakaoOAuthClient.getUserInfoByCode(code);
            case APPLE -> throw new GeneralException(AuthErrorCode.UNSUPPORTED_PROVIDER);
        };
    }

    // Temp Token 페이로드
    public record TempTokenPayload(
            AuthProvider provider,
            String providerUserId
    ) {}
}
