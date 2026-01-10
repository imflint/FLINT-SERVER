package kr.flint.api.domain.auth.dto.response;

import lombok.Builder;

@Builder
public record AuthTokenRes(
        String accessToken,
        String refreshToken,
        Long userId
) {
    public static AuthTokenRes of(String accessToken, String refreshToken, Long userId) {
        return AuthTokenRes.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(userId)
                .build();
    }
}
