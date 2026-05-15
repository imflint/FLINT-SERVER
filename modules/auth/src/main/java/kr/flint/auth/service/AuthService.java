package kr.flint.auth.service;

import kr.flint.auth.dto.AuthTokens;
import kr.flint.auth.dto.RefreshTokenValue;
import kr.flint.auth.dto.SocialUserInfo;
import kr.flint.auth.dto.SocialVerifyResult;
import kr.flint.auth.dto.TempTokenPayload;
import kr.flint.auth.domain.UserIdentity;
import kr.flint.auth.enums.AuthProvider;
import kr.flint.auth.enums.RefreshTokenStatus;
import kr.flint.auth.enums.TokenAudience;
import kr.flint.auth.enums.TokenType;
import kr.flint.auth.exception.AuthErrorCode;
import kr.flint.auth.jwt.JwtProvider;
import kr.flint.auth.jwt.AccessTokenBlacklist;
import kr.flint.auth.repository.RefreshTokenRepository;
import kr.flint.auth.exception.AuthException;
import kr.flint.auth.repository.UserIdentityRepository;
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
    private final UserIdentityRepository userIdentityRepository;

    // 소셜 사용자 정보로 로그인/회원가입 분기 처리
    public SocialVerifyResult verifySocialUser(AuthProvider provider, SocialUserInfo userInfo) {
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
        if (!jwtProvider.isTokenType(tempToken, TokenType.TEMP)) {
            throw new AuthException(AuthErrorCode.INVALID_TOKEN);
        }

        AuthProvider provider = jwtProvider.getProvider(tempToken);
        String providerUserId = jwtProvider.getProviderUserId(tempToken);

        return new TempTokenPayload(provider, providerUserId);
    }

    // Access / Refresh Token 발급
    @Transactional
    public AuthTokens issueTokens(Long userId, String role) {
        return issueTokens(userId, role, TokenAudience.USER);
    }

    @Transactional
    public AuthTokens issueTokens(Long userId, String role, TokenAudience audience) {
        String accessToken = jwtProvider.createAccessToken(userId, role, audience);
        String refreshToken = refreshTokenRepository.createToken();

        // Redis에 Refresh Token 저장 (token → userId)
        long ttlSeconds = jwtProvider.getRefreshTokenTtlSeconds();
        refreshTokenRepository.save(refreshToken, userId, audience, ttlSeconds);

        return AuthTokens.of(accessToken, refreshToken, userId);
    }

    // Refresh Token 검증 및 Rotation (원자적 상태 변경)
    @Transactional
    public Long validateAndRotateToken(String refreshToken) {
        return validateAndRotateToken(refreshToken, TokenAudience.USER);
    }

    @Transactional
    public Long validateAndRotateToken(String refreshToken, TokenAudience expectedAudience) {
        RefreshTokenValue currentTokenValue = refreshTokenRepository.findByToken(refreshToken)
            .orElseThrow(() -> new AuthException(AuthErrorCode.REFRESH_TOKEN_NOT_FOUND));
        if (currentTokenValue.audienceOrDefault() != expectedAudience) {
            throw new AuthException(AuthErrorCode.INVALID_TOKEN);
        }

        // 원자적으로 VALID → USED 변경 시도 (변경 전 상태 반환)
        RefreshTokenValue tokenValue = refreshTokenRepository.markAsUsedIfValid(refreshToken)
                .orElseThrow(() -> new AuthException(AuthErrorCode.REFRESH_TOKEN_NOT_FOUND));

        // 원본 상태 확인 (VALID였으면 이미 USED로 변경됨)
        switch (tokenValue.status()) {
            case USED -> {
                log.warn("토큰 탈취 감지 userId: {}", tokenValue.userId());
                refreshTokenRepository.revokeAllByUserId(tokenValue.userId());
                throw new AuthException(AuthErrorCode.REFRESH_TOKEN_REUSED);
            }
            case REVOKED -> throw new AuthException(AuthErrorCode.REFRESH_TOKEN_REVOKED);
            case VALID -> {} // 정상 - 이미 USED로 변경됨
        }

        if (tokenValue.isExpired()) {
            throw new AuthException(AuthErrorCode.EXPIRED_TOKEN);
        }

        return tokenValue.userId();
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
        if (accessToken != null) {
            long remainingTtl = jwtProvider.getRemainingTtlSeconds(accessToken);
            if (remainingTtl > 0) {
                accessTokenBlacklist.blacklist(accessToken, remainingTtl);
            }
        }

        refreshTokenRepository.deleteAllByUserId(userId);
    }

    // 회원탈퇴 (모든 Refresh Token 및 소셜 인증 정보 삭제)
    @Transactional
    public void withdraw(Long userId, String accessToken) {
        // Access Token Blacklist 추가
        if (accessToken != null) {
            long remainingTtl = jwtProvider.getRemainingTtlSeconds(accessToken);
            if (remainingTtl > 0) {
                accessTokenBlacklist.blacklist(accessToken, remainingTtl);
            }
        }

        // 모든 Refresh Token 삭제
        refreshTokenRepository.deleteAllByUserId(userId);
        userIdentityRepository.deleteAllByUserId(userId);
    }

}
