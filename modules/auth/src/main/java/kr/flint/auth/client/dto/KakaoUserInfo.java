package kr.flint.auth.client.dto;

public record KakaoUserInfo(
        String providerUserId
) {
    public static KakaoUserInfo from(KakaoUserResponse response) {
        return new KakaoUserInfo(String.valueOf(response.id()));
    }
}
