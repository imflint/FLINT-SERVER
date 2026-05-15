package kr.flint.admin.domain.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 사용자 통계 응답")
public record AdminUserStatisticsRes(
    @Schema(description = "현재 활성 사용자 수", example = "1234")
    long activeUserCount
) {
    public static AdminUserStatisticsRes of(long activeUserCount) {
        return new AdminUserStatisticsRes(activeUserCount);
    }
}
