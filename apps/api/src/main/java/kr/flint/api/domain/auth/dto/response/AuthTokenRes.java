package kr.flint.api.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.flint.auth.dto.AuthTokens;
import lombok.Builder;

@Schema(description = "인증 토큰 응답")
@Builder
public record AuthTokenRes(
        @Schema(description = "JWT 액세스 토큰 (API 요청 시 Authorization 헤더에 사용)", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
        String accessToken,

        @Schema(description = "리프레시 토큰 (액세스 토큰 갱신 시 사용)", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
        String refreshToken,

        @Schema(description = "사용자 ID", example = "123456789")
        Long userId
) {
    public static AuthTokenRes from(AuthTokens tokens) {
        return AuthTokenRes.builder()
                .accessToken(tokens.accessToken())
                .refreshToken(tokens.refreshToken())
                .userId(tokens.userId())
                .build();
    }
}
