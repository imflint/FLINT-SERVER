package kr.flint.admin.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "관리자 토큰 갱신 요청")
public record AdminRefreshTokenReq(
    @Schema(description = "관리자 Refresh Token", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "refreshToken은 필수입니다")
    String refreshToken
) {
}
