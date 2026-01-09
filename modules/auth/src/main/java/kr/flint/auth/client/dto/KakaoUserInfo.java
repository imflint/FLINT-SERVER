package kr.flint.auth.client.dto;

public record KakaoUserInfo(
        String providerUserId,
        String email,
        String nickname
) {
    public static KakaoUserInfo from(KakaoUserResponse response) {
        String email = null;
        String nickname = null;

        if (response.kakaoAccount() != null) {
            email = response.kakaoAccount().email();
            if (response.kakaoAccount().profile() != null) {
                nickname = response.kakaoAccount().profile().nickname();
            }
        }

        return new KakaoUserInfo(
                String.valueOf(response.id()),
                email,
                nickname
        );
    }
}
