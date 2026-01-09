package kr.flint.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import kr.flint.auth.config.JwtProperties;
import kr.flint.auth.domain.enums.AuthProvider;
import kr.flint.auth.exception.AuthErrorCode;
import kr.flint.shared.exception.GeneralException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
public class JwtProvider {

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TOKEN_ID = "tokenId";
    private static final String CLAIM_PROVIDER = "provider";
    private static final String CLAIM_PROVIDER_USER_ID = "providerUserId";
    private static final String CLAIM_TOKEN_TYPE = "type";

    private static final String TOKEN_TYPE_ACCESS = "ACCESS";
    private static final String TOKEN_TYPE_REFRESH = "REFRESH";
    private static final String TOKEN_TYPE_TEMP = "TEMP";

    private final SecretKey secretKey;
    private final JwtProperties jwtProperties;

    public JwtProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }

    // Access Token 생성 (userId, role 포함)
    public String createAccessToken(Long userId, String role) {
        Instant now = Instant.now();
        Instant expiry = now.plus(jwtProperties.accessExpiration());

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_ROLE, role)
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(secretKey)
                .compact();
    }

    // Refresh Token 생성 (tokenId 포함)
    public String createRefreshToken(Long userId) {
        Instant now = Instant.now();
        Instant expiry = now.plus(jwtProperties.refreshExpiration());
        String tokenId = UUID.randomUUID().toString();

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_TOKEN_ID, tokenId)
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_REFRESH)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(secretKey)
                .compact();
    }

    // Temp Token 생성 (소셜 정보 포함, 회원가입용)
    public String createTempToken(AuthProvider provider, String providerUserId) {
        Instant now = Instant.now();
        Instant expiry = now.plus(jwtProperties.tempExpiration());

        return Jwts.builder()
                .claim(CLAIM_PROVIDER, provider.name())
                .claim(CLAIM_PROVIDER_USER_ID, providerUserId)
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_TEMP)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(secretKey)
                .compact();
    }

    // 토큰 검증 및 Claims 추출
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new GeneralException(AuthErrorCode.EXPIRED_TOKEN);
        } catch (JwtException e) {
            log.warn("JWT 파싱 실패: {}", e.getMessage());
            throw new GeneralException(AuthErrorCode.INVALID_TOKEN);
        }
    }

    // 토큰에서 userId 추출
    public Long getUserId(String token) {
        Claims claims = parseToken(token);
        return claims.get(CLAIM_USER_ID, Long.class);
    }

    // 토큰에서 role 추출
    public String getRole(String token) {
        Claims claims = parseToken(token);
        return claims.get(CLAIM_ROLE, String.class);
    }

    // 토큰에서 tokenId 추출 (Refresh Token용)
    public String getTokenId(String token) {
        Claims claims = parseToken(token);
        return claims.get(CLAIM_TOKEN_ID, String.class);
    }

    // Temp Token에서 provider 추출
    public AuthProvider getProvider(String token) {
        Claims claims = parseToken(token);
        String providerName = claims.get(CLAIM_PROVIDER, String.class);
        return AuthProvider.valueOf(providerName);
    }

    // Temp Token에서 providerUserId 추출
    public String getProviderUserId(String token) {
        Claims claims = parseToken(token);
        return claims.get(CLAIM_PROVIDER_USER_ID, String.class);
    }

    // 토큰 타입 확인
    public boolean isAccessToken(String token) {
        Claims claims = parseToken(token);
        return TOKEN_TYPE_ACCESS.equals(claims.get(CLAIM_TOKEN_TYPE, String.class));
    }

    public boolean isRefreshToken(String token) {
        Claims claims = parseToken(token);
        return TOKEN_TYPE_REFRESH.equals(claims.get(CLAIM_TOKEN_TYPE, String.class));
    }

    public boolean isTempToken(String token) {
        Claims claims = parseToken(token);
        return TOKEN_TYPE_TEMP.equals(claims.get(CLAIM_TOKEN_TYPE, String.class));
    }

    // Refresh Token TTL 반환 (Redis 저장용)
    public long getRefreshTokenTtlSeconds() {
        return jwtProperties.refreshExpiration().toSeconds();
    }
}
