package kr.flint.admin.domain.user.dto.request;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kr.flint.moderation.domain.UserModerationAction;

@Schema(description = "관리자 회원 제재 요청")
public record AdminUserModerationReq(
    @Schema(description = "회원 제재 조치", allowableValues = {"WARN", "RESTRICT_UPLOAD", "SUSPEND"}, example = "WARN")
    @NotNull(message = "회원 조치를 선택해주세요.")
    UserModerationAction action,

    @Schema(description = "업로드 제한 또는 이용 정지 종료 시각", example = "2026-05-31T23:59:59")
    LocalDateTime expiresAt,

    @Schema(description = "관리자 메모", maxLength = 500)
    @Size(max = 500, message = "관리자 메모는 최대 500자까지 입력할 수 있습니다.")
    String adminMemo
) {
}
