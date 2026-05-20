package kr.flint.admin.domain.user.service;

import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import kr.flint.user.service.DailyUserMetricsAggregationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "flint.admin.metrics.scheduling.enabled", havingValue = "true")
public class DailyUserMetricsScheduler {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    private final DailyUserMetricsAggregationService dailyUserMetricsAggregationService;

    @EventListener(ApplicationReadyEvent.class)
    public void aggregateMissingMetricsOnStartup() {
        aggregateMissingMetrics();
    }

    @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Seoul")
    public void aggregateMissingMetrics() {
        LocalDate endDate = LocalDate.now(SEOUL_ZONE).minusDays(1);
        log.info("[scheduled] dailyUserMetricsAggregation until {}", endDate);
        dailyUserMetricsAggregationService.aggregateMissingMetricsUntil(endDate);
    }
}
