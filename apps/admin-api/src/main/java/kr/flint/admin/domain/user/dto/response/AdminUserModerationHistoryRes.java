package kr.flint.admin.domain.user.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.flint.moderation.domain.UserModerationAction;
import kr.flint.moderation.domain.UserModerationHistory;

@Schema(description = "관리자 회원 제재 이력")
public record AdminUserModerationHistoryRes(
    Long historyId,
    Long adminId,
    UserModerationAction action,
    LocalDateTime actionExpiresAt,
    String adminMemo,
    LocalDateTime createdAt
) {
    public static AdminUserModerationHistoryRes from(UserModerationHistory history) {
        return new AdminUserModerationHistoryRes(
            history.getId(),
            history.getAdminUserId(),
            history.getAction(),
            history.getActionExpiresAt(),
            history.getAdminMemo(),
            history.getCreatedAt()
        );
    }
}
