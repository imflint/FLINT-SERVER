package kr.flint.admin.domain.user.service;

import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import kr.flint.admin.common.AdminAuthorizationService;
import kr.flint.admin.domain.user.dto.request.AdminDailyUserMetricsRange;
import kr.flint.admin.domain.user.dto.response.AdminDailyUserMetricsRes;
import kr.flint.admin.domain.user.dto.response.AdminDailyUserMetricsRes.DailyUserMetricRes;
import kr.flint.admin.domain.user.dto.response.AdminUserStatisticsRes;
import kr.flint.user.service.DailyUserMetricsAggregationService;
import kr.flint.user.service.UserService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserStatisticsFacade {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
    private static final int DAYS_7 = 7;
    private static final int DAYS_30 = 30;

    private final AdminAuthorizationService adminAuthorizationService;
    private final UserService userService;
    private final DailyUserMetricsAggregationService dailyUserMetricsAggregationService;

    public AdminUserStatisticsRes getStatistics(Long adminId) {
        adminAuthorizationService.validateAdmin(adminId);
        return AdminUserStatisticsRes.of(userService.countActiveUsers());
    }

    public AdminDailyUserMetricsRes getDailyActivity(
        Long adminId,
        AdminDailyUserMetricsRange range,
        LocalDate from,
        LocalDate to
    ) {
        adminAuthorizationService.validateAdmin(adminId);
        LocalDate end = to == null ? LocalDate.now(SEOUL_ZONE).minusDays(1) : to;
        LocalDate start = resolveStartDate(range, from, end);
        return AdminDailyUserMetricsRes.from(dailyUserMetricsAggregationService.getDailyMetrics(start, end).stream()
            .map(metrics -> DailyUserMetricRes.of(
                metrics.date(),
                metrics.visitorCount(),
                metrics.signupUserCount(),
                metrics.memberCount()
            ))
            .toList());
    }

    private LocalDate resolveStartDate(AdminDailyUserMetricsRange range, LocalDate from, LocalDate end) {
        if (from != null) {
            return from;
        }

        AdminDailyUserMetricsRange resolvedRange = range == null ? AdminDailyUserMetricsRange.DAYS_30 : range;
        return switch (resolvedRange) {
            case DAYS_7 -> end.minusDays(DAYS_7 - 1);
            case DAYS_30 -> end.minusDays(DAYS_30 - 1);
            case ALL -> resolveAllStartDate(end);
        };
    }

    private LocalDate resolveAllStartDate(LocalDate end) {
        return dailyUserMetricsAggregationService.findFirstMetricDate().orElse(end);
    }
}
