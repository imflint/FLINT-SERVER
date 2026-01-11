package kr.flint.auth.dto;

import kr.flint.auth.enums.RefreshTokenStatus;

import java.time.Instant;

public record RefreshTokenValue(
        Long userId,
        RefreshTokenStatus status,
        Instant expiresAt
) {
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public RefreshTokenValue withStatus(RefreshTokenStatus newStatus) {
        return new RefreshTokenValue(userId, newStatus, expiresAt);
    }

    public static RefreshTokenValue createValid(Long userId, long ttlSeconds) {
        return new RefreshTokenValue(
                userId,
                RefreshTokenStatus.VALID,
                Instant.now().plusSeconds(ttlSeconds)
        );
    }
}
