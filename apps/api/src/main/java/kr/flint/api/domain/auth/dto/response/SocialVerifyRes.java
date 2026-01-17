package kr.flint.api.domain.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Schema(description = "소셜 로그인 인증 결과")
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SocialVerifyRes(
        @Schema(description = "기존 회원 여부 (true: 로그인 완료, false: 회원가입 필요)", example = "true")
        boolean isRegistered,

        @Schema(description = "JWT 액세스 토큰 (기존 회원인 경우에만 반환)", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
        String accessToken,

        @Schema(description = "리프레시 토큰 (기존 회원인 경우에만 반환)", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
        String refreshToken,

        @Schema(description = "사용자 ID (기존 회원인 경우에만 반환)", example = "123456789")
        Long userId,

        @Schema(description = "임시 토큰 (신규 회원인 경우에만 반환 - 회원가입 시 사용)", example = "temp_token_xxx")
        String tempToken
) {
    public static SocialVerifyRes registered(String accessToken, String refreshToken, Long userId) {
        return SocialVerifyRes.builder()
                .isRegistered(true)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(userId)
                .build();
    }

    public static SocialVerifyRes unregistered(String tempToken) {
        return SocialVerifyRes.builder()
                .isRegistered(false)
                .tempToken(tempToken)
                .build();
    }
}
