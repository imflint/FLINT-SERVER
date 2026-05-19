package kr.flint.admin.domain.user.dto.response;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 일별 사용자 지표 응답")
public record AdminDailyUserMetricsRes(
    @Schema(description = "일별 사용자 지표 목록")
    List<DailyUserMetricRes> dailyMetrics
) {
    public static AdminDailyUserMetricsRes from(List<DailyUserMetricRes> dailyMetrics) {
        return new AdminDailyUserMetricsRes(dailyMetrics);
    }

    @Schema(description = "일별 사용자 지표")
    public record DailyUserMetricRes(
        @Schema(description = "집계 날짜", example = "2026-05-01")
        LocalDate date,
        @Schema(description = "해당 날짜의 고유 방문자 수", example = "12")
        long visitorCount,
        @Schema(description = "해당 날짜의 신규 가입 수", example = "3")
        long signupUserCount,
        @Schema(description = "해당 날짜 기준 전체 회원 수", example = "123")
        long memberCount
    ) {
        public static DailyUserMetricRes of(
            LocalDate date,
            long visitorCount,
            long signupUserCount,
            long memberCount
        ) {
            return new DailyUserMetricRes(date, visitorCount, signupUserCount, memberCount);
        }
    }
}
