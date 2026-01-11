package kr.flint.auth.dto;

public record AuthTokens(
        String accessToken,
        String refreshToken,
        Long userId
) {
    public static AuthTokens of(String accessToken, String refreshToken, Long userId) {
        return new AuthTokens(accessToken, refreshToken, userId);
    }
}
