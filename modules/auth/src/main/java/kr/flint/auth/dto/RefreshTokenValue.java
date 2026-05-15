package kr.flint.auth.dto;

import kr.flint.auth.enums.RefreshTokenStatus;
import kr.flint.auth.enums.TokenAudience;

import java.time.Instant;

public record RefreshTokenValue(
    Long userId,
    RefreshTokenStatus status,
    Instant expiresAt,
    TokenAudience audience
) {
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public TokenAudience audienceOrDefault() {
        return audience == null ? TokenAudience.USER : audience;
    }

    public RefreshTokenValue withStatus(RefreshTokenStatus newStatus) {
        return new RefreshTokenValue(userId, newStatus, expiresAt, audience);
    }

    public RefreshTokenValue withAudienceIfMissing(TokenAudience fallbackAudience) {
        return audience == null ? new RefreshTokenValue(userId, status, expiresAt, fallbackAudience) : this;
    }

    public static RefreshTokenValue createValid(Long userId, TokenAudience audience, long ttlSeconds) {
        return new RefreshTokenValue(
            userId,
            RefreshTokenStatus.VALID,
            Instant.now().plusSeconds(ttlSeconds),
            audience
        );
    }
}
