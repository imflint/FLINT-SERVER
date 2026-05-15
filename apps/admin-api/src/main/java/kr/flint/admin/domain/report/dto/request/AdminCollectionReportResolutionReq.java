package kr.flint.admin.domain.report.dto.request;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kr.flint.moderation.domain.CollectionModerationAction;
import kr.flint.moderation.domain.UserModerationAction;

@Schema(description = "컬렉션 신고 처리 요청")
public record AdminCollectionReportResolutionReq(
    @Schema(description = "컬렉션 조치", example = "HIDE", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "컬렉션 조치는 필수입니다.")
    CollectionModerationAction collectionAction,

    @Schema(description = "사용자 조치", example = "WARN", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "사용자 조치는 필수입니다.")
    UserModerationAction userAction,

    @Schema(description = "사용자 조치 만료 시각. null이면 무기한 제한입니다.", example = "2026-06-30T23:59:59")
    LocalDateTime userActionExpiresAt,

    @Schema(description = "관리자 처리 메모", example = "욕설 신고 확인 후 숨김 처리")
    @Size(max = 500, message = "관리자 메모는 500자 이하여야 합니다.")
    String adminMemo
) {
}
