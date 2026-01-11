package kr.flint.auth.dto;

public record SocialVerifyResult(
        boolean isRegistered,
        Long userId,
        String tempToken
) {
    public static SocialVerifyResult registered(Long userId) {
        return new SocialVerifyResult(true, userId, null);
    }

    public static SocialVerifyResult unregistered(String tempToken) {
        return new SocialVerifyResult(false, null, tempToken);
    }
}
