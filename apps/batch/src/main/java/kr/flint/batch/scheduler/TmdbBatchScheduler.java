package kr.flint.batch.scheduler;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import kr.flint.batch.sync.TmdbCatalogCoordinator;
import kr.flint.shared.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "flint.batch.scheduling.enabled", havingValue = "true")
@Slf4j
public class TmdbBatchScheduler {
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final TmdbCatalogCoordinator coordinator;

    @Scheduled(cron = "0 0 2 1 * *", zone = "Asia/Seoul")
    public void runMonthlyReconcile() {
        coordinator.startMonthly(YearMonth.now(KST));
    }

    @Scheduled(cron = "0 */10 5-23 * * *", zone = "Asia/Seoul")
    public void runDailySync() {
        try {
            coordinator.startDaily(LocalDate.now(KST));
        } catch (GeneralException exception) {
            log.info("Daily TMDB sync waits for the active workflow: {}", exception.getMessage());
        }
    }
}
