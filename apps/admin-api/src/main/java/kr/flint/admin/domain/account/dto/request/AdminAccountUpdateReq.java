package kr.flint.admin.domain.account.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "관리자 계정 정보 수정 요청")
public record AdminAccountUpdateReq(
    @Schema(description = "관리자 로그인 ID", example = "admin", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "아이디는 필수입니다.")
    @Size(max = 100, message = "아이디는 100자 이하여야 합니다.")
    String username,

    @Schema(description = "현재 비밀번호", example = "current-password", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "현재 비밀번호는 필수입니다.")
    String currentPassword,

    @Schema(description = "새 비밀번호. 미입력 시 비밀번호를 변경하지 않습니다.", example = "new-password")
    @Size(min = 8, max = 100, message = "새 비밀번호는 8자 이상 100자 이하여야 합니다.")
    String newPassword
) {
}
