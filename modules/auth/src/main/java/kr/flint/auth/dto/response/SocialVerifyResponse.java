package kr.flint.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SocialVerifyResponse(
        boolean isRegistered,
        String accessToken,
        String refreshToken,
        Long userId,
        String tempToken,
        String email
) {
    // 기존 회원 로그인 응답
    public static SocialVerifyResponse registered(String accessToken, String refreshToken, Long userId) {
        return SocialVerifyResponse.builder()
                .isRegistered(true)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(userId)
                .build();
    }

    // 신규 회원 (회원가입 필요) 응답
    public static SocialVerifyResponse unregistered(String tempToken, String email) {
        return SocialVerifyResponse.builder()
                .isRegistered(false)
                .tempToken(tempToken)
                .email(email)
                .build();
    }
}
