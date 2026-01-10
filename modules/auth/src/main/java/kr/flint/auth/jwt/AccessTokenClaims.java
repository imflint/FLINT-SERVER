package kr.flint.auth.jwt;

public record AccessTokenClaims(
        Long userId,
        String role
) {
    public boolean isValid() {
        return userId != null;
    }
}
