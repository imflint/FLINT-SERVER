package kr.flint.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import kr.flint.auth.config.JwtProperties;
import kr.flint.auth.domain.enums.AuthProvider;
import kr.flint.auth.exception.AuthErrorCode;
import kr.flint.auth.exception.AuthException;
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
    private static final String CLAIM_PROVIDER = "provider";
    private static final String CLAIM_PROVIDER_USER_ID = "providerUserId";
    private static final String CLAIM_TOKEN_TYPE = "type";

    private static final String TOKEN_TYPE_ACCESS = "ACCESS";
    private static final String TOKEN_TYPE_TEMP = "TEMP";

    private final SecretKey secretKey;
    private final JwtProperties jwtProperties;

    public JwtProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }

    // Access Token 생성
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

    // Refresh Token
    public String createRefreshToken() {
        return UUID.randomUUID().toString();
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

    // JWT 토큰 검증 및 Claims 추출
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new AuthException(AuthErrorCode.EXPIRED_TOKEN);
        } catch (JwtException e) {
            log.warn("JWT 파싱 실패: {}", e.getMessage());
            throw new AuthException(AuthErrorCode.INVALID_TOKEN);
        }
    }

    public Long getUserId(String token) {
        Claims claims = parseToken(token);
        return claims.get(CLAIM_USER_ID, Long.class);
    }

    public String getRole(String token) {
        Claims claims = parseToken(token);
        return claims.get(CLAIM_ROLE, String.class);
    }

    public AuthProvider getProvider(String token) {
        Claims claims = parseToken(token);
        String providerName = claims.get(CLAIM_PROVIDER, String.class);
        return AuthProvider.valueOf(providerName);
    }

    public String getProviderUserId(String token) {
        Claims claims = parseToken(token);
        return claims.get(CLAIM_PROVIDER_USER_ID, String.class);
    }

    // Access Token 파싱 및 Claims 추출 (인증 필터용)
    public AccessTokenClaims parseAccessToken(String token) {
        Claims claims = parseToken(token);
        String tokenType = claims.get(CLAIM_TOKEN_TYPE, String.class);

        if (!TOKEN_TYPE_ACCESS.equals(tokenType)) {
            return null;
        }

        return new AccessTokenClaims(
                claims.get(CLAIM_USER_ID, Long.class),
                claims.get(CLAIM_ROLE, String.class)
        );
    }

    public boolean isAccessToken(String token) {
        try {
            Claims claims = parseToken(token);
            return TOKEN_TYPE_ACCESS.equals(claims.get(CLAIM_TOKEN_TYPE, String.class));
        } catch (AuthException e) {
            return false;
        }
    }

    public boolean isTempToken(String token) {
        try {
            Claims claims = parseToken(token);
            return TOKEN_TYPE_TEMP.equals(claims.get(CLAIM_TOKEN_TYPE, String.class));
        } catch (AuthException e) {
            return false;
        }
    }

    // Refresh Token TTL 반환 (Redis 저장용)
    public long getRefreshTokenTtlSeconds() {
        return jwtProperties.refreshExpiration().toSeconds();
    }

    // Access Token 남은 TTL 계산 (Blacklist용)
    public long getRemainingTtlSeconds(String token) {
        try {
            Claims claims = parseTokenAllowExpired(token);
            Date expiration = claims.getExpiration();
            long remainingMs = expiration.getTime() - System.currentTimeMillis();
            return Math.max(0, remainingMs / 1000);
        } catch (Exception e) {
            return 0;
        }
    }

    // Bearer 토큰에서 JWT 추출
    public String extractToken(String bearerToken) {
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    // 만료된 토큰도 파싱 허용 (내부 사용 - Claims 추출용)
    private Claims parseTokenAllowExpired(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            // 만료된 토큰이라도 Claims는 반환
            return e.getClaims();
        } catch (JwtException e) {
            log.warn("JWT 파싱 실패: {}", e.getMessage());
            throw new AuthException(AuthErrorCode.INVALID_TOKEN);
        }
    }
}
