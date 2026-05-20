package kr.flint.admin.domain.user.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.flint.user.domain.UserRole;
import kr.flint.user.domain.UserStatus;

@Schema(description = "관리자 회원 목록 항목")
public record AdminUserSummaryRes(
    Long userId,
    String nickname,
    String profileImageUrl,
    UserRole userRole,
    UserStatus status,
    int warningCount,
    boolean uploadRestricted,
    LocalDateTime uploadRestrictedUntil,
    boolean suspended,
    LocalDateTime suspendedUntil,
    LocalDateTime createdAt
) {
}
