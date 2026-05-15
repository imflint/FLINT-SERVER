package kr.flint.auth.jwt.dto;

import kr.flint.auth.enums.TokenAudience;

public record AccessTokenInfo(
    Long userId,
    String role,
    TokenAudience audience
) {
    public boolean isValid() {
        return userId != null;
    }

    public boolean isAudience(TokenAudience expectedAudience) {
        return audience == expectedAudience;
    }
}
