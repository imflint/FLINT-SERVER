package kr.flint.auth.service;

import kr.flint.auth.client.KakaoOAuthClient;
import kr.flint.auth.client.dto.KakaoUserInfo;
import kr.flint.auth.domain.UserIdentity;
import kr.flint.auth.domain.enums.AuthProvider;
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

    // Access/Refresh Token 발급
    @Transactional
    public AuthTokenResponse issueTokens(Long userId, String role) {
        String accessToken = jwtProvider.createAccessToken(userId, role);
        String refreshToken = jwtProvider.createRefreshToken(userId);

        // Redis에 Refresh Token 저장
        String tokenId = jwtProvider.getTokenId(refreshToken);
        long ttlSeconds = jwtProvider.getRefreshTokenTtlSeconds();
        refreshTokenRepository.save(userId, tokenId, refreshToken, ttlSeconds);

        return AuthTokenResponse.of(accessToken, refreshToken, userId);
    }

    // Refresh Token으로 토큰 갱신
    @Transactional
    public AuthTokenResponse refreshTokens(String refreshToken) {
        if (!jwtProvider.isRefreshToken(refreshToken)) {
            throw new GeneralException(AuthErrorCode.INVALID_TOKEN);
        }

        Long userId = jwtProvider.getUserId(refreshToken);
        String tokenId = jwtProvider.getTokenId(refreshToken);

        // Redis에서 Refresh Token 검증
        refreshTokenRepository.findByUserIdAndTokenId(userId, tokenId)
                .orElseThrow(() -> new GeneralException(AuthErrorCode.REFRESH_TOKEN_NOT_FOUND));

        // 기존 Refresh Token 삭제
        refreshTokenRepository.deleteByUserIdAndTokenId(userId, tokenId);

        // 새 토큰 발급 (role은 기존 토큰에서 가져올 수 없으므로 null)
        return issueTokens(userId, null);
    }

    // 로그아웃 (현재 Refresh Token 삭제)
    @Transactional
    public void logout(Long userId, String refreshToken) {
        if (refreshToken != null && jwtProvider.isRefreshToken(refreshToken)) {
            String tokenId = jwtProvider.getTokenId(refreshToken);
            refreshTokenRepository.deleteByUserIdAndTokenId(userId, tokenId);
        }
    }

    // 전체 로그아웃 (모든 Refresh Token 삭제)
    @Transactional
    public void logoutAll(Long userId) {
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
