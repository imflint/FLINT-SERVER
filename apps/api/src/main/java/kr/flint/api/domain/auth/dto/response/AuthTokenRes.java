package kr.flint.api.domain.auth.dto.response;

import kr.flint.auth.dto.AuthTokens;
import lombok.Builder;

@Builder
public record AuthTokenRes(
        String accessToken,
        String refreshToken,
        Long userId
) {
    public static AuthTokenRes from(AuthTokens tokens) {
        return AuthTokenRes.builder()
                .accessToken(tokens.accessToken())
                .refreshToken(tokens.refreshToken())
                .userId(tokens.userId())
                .build();
    }
}
