package kr.flint.auth.jwt.dto;

public record AccessTokenInfo(
        Long userId,
        String role
) {
    public boolean isValid() {
        return userId != null;
    }
}
