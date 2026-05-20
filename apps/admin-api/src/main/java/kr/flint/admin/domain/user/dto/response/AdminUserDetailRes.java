package kr.flint.admin.domain.user.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.flint.user.domain.UserRole;
import kr.flint.user.domain.UserStatus;

@Schema(description = "관리자 회원 상세")
public record AdminUserDetailRes(
    Long userId,
    String nickname,
    String profileImageUrl,
    UserRole userRole,
    UserStatus status,
    int warningCount,
    boolean uploadRestricted,
    LocalDateTime uploadRestrictedAt,
    LocalDateTime uploadRestrictedUntil,
    boolean suspended,
    LocalDateTime suspendedAt,
    LocalDateTime suspendedUntil,
    LocalDateTime deletedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<AdminUserModerationHistoryRes> recentModerations
) {
}
