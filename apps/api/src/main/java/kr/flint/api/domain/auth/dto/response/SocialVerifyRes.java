package kr.flint.api.domain.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SocialVerifyRes(
        boolean isRegistered,
        String accessToken,
        String refreshToken,
        Long userId,
        String tempToken
) {
    // 기존 회원 로그인 응답
    public static SocialVerifyRes registered(String accessToken, String refreshToken, Long userId) {
        return SocialVerifyRes.builder()
                .isRegistered(true)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(userId)
                .build();
    }

    // 신규 회원 (회원가입 필요) 응답
    public static SocialVerifyRes unregistered(String tempToken) {
        return SocialVerifyRes.builder()
                .isRegistered(false)
                .tempToken(tempToken)
                .build();
    }
}
