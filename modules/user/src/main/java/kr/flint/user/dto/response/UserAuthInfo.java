package kr.flint.user.dto.response;

public record UserAuthInfo(
        Long userId,
		String nickname,
        String role
) {
    public static UserAuthInfo of(Long userId, String nickname, String role) {
        return new UserAuthInfo(userId, nickname, role);
    }
}
