package kr.flint.user.dto.response;

import java.time.LocalDate;

public record DailyUserMetricsRes(
    LocalDate date,
    long visitorCount,
    long signupUserCount,
    long memberCount
) {
    public static DailyUserMetricsRes of(
        LocalDate date,
        long visitorCount,
        long signupUserCount,
        long memberCount
    ) {
        return new DailyUserMetricsRes(date, visitorCount, signupUserCount, memberCount);
    }
}
