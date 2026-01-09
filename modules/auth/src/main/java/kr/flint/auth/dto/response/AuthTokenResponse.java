package kr.flint.auth.dto.response;

import lombok.Builder;

@Builder
public record AuthTokenResponse(
        String accessToken,
        String refreshToken,
        Long userId
) {
    public static AuthTokenResponse of(String accessToken, String refreshToken, Long userId) {
        return AuthTokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(userId)
                .build();
    }
}
