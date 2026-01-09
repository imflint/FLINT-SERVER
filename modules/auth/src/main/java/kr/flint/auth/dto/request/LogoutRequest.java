package kr.flint.auth.dto.request;

public record LogoutRequest(
        String refreshToken
) {
}
