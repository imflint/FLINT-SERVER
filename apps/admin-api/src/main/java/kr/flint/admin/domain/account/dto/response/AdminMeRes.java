package kr.flint.admin.domain.account.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.flint.adminauth.domain.Admin;

@Schema(description = "관리자 본인 계정 정보")
public record AdminMeRes(
    @Schema(description = "관리자 ID", example = "1")
    Long adminId,

    @Schema(description = "관리자 로그인 ID", example = "admin")
    String username,

    @Schema(description = "비밀번호 마지막 변경 시각", example = "2026-05-18T10:00:00")
    LocalDateTime passwordChangedAt,

    @Schema(description = "계정 생성 시각", example = "2026-05-18T10:00:00")
    LocalDateTime createdAt,

    @Schema(description = "계정 수정 시각", example = "2026-05-18T10:00:00")
    LocalDateTime updatedAt
) {

    public static AdminMeRes from(Admin admin) {
        return new AdminMeRes(
            admin.getId(),
            admin.getUsername(),
            admin.getPasswordChangedAt(),
            admin.getCreatedAt(),
            admin.getUpdatedAt()
        );
    }
}
