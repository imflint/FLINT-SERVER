package kr.flint.admin.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.flint.adminauth.domain.Admin;
import kr.flint.auth.dto.AuthTokens;

@Schema(description = "관리자 로그인 응답")
public record AdminLoginRes(
    @Schema(description = "Access Token")
    String accessToken,

    @Schema(description = "Refresh Token")
    String refreshToken,

    @Schema(description = "관리자 ID", example = "1")
    Long adminId
) {

    public static AdminLoginRes from(AuthTokens tokens, Admin admin) {
        return new AdminLoginRes(
            tokens.accessToken(),
            tokens.refreshToken(),
            admin.getId()
        );
    }
}
