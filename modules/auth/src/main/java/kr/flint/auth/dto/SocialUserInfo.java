package kr.flint.auth.dto;

public record SocialUserInfo(
        String providerUserId
) {
    public static SocialUserInfo of(String providerUserId) {
        return new SocialUserInfo(providerUserId);
    }
}
