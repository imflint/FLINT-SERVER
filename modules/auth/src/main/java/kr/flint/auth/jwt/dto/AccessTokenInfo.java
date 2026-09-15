package kr.flint.auth.jwt.dto;

import java.time.Instant;

import kr.flint.auth.enums.TokenAudience;

public record AccessTokenInfo(
    Long userId,
    String role,
    TokenAudience audience,
    Instant issuedAt
) {
    public boolean isValid() {
        return userId != null && audience != null && issuedAt != null;
    }

    public boolean isAudience(TokenAudience expectedAudience) {
        return expectedAudience != null && audience == expectedAudience;
    }
}
