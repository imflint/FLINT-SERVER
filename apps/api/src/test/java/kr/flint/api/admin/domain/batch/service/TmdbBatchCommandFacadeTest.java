package kr.flint.api.admin.domain.batch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import kr.flint.api.admin.domain.batch.dto.response.BatchJobExecutionRes;
import kr.flint.batch.sync.TmdbCatalogCoordinator;
import kr.flint.batch.sync.TmdbSyncRun;
import kr.flint.batch.sync.TmdbSyncRunStatus;
import kr.flint.batch.sync.TmdbSyncRunType;
import kr.flint.content.domain.MediaType;

@ExtendWith(MockitoExtension.class)
class TmdbBatchCommandFacadeTest {

    @Mock
    private TmdbCatalogCoordinator coordinator;

    private TmdbBatchCommandFacade facade;

    @BeforeEach
    void setUp() {
        facade = new TmdbBatchCommandFacade(coordinator);
    }

    @Test
    @DisplayName("일간 동기화는 DAILY 업무 키의 기존 또는 신규 실행을 반환")
    void triggerDailySync() {
        LocalDate date = LocalDate.of(2026, 9, 13);
        when(coordinator.startDaily(date)).thenReturn(run("DAILY:2026-09-13", TmdbSyncRunType.DAILY, date));

        BatchJobExecutionRes result = facade.triggerDailySync(date);

        assertThat(result.jobName()).isEqualTo("DAILY:2026-09-13");
        verify(coordinator).startDaily(date);
    }

    @Test
    @DisplayName("월간 조정은 MONTHLY 업무 키로 실행")
	void triggerMonthlyReconcile() {
        YearMonth month = YearMonth.of(2026, 9);
        LocalDate date = month.atDay(1);
        when(coordinator.startMonthly(month)).thenReturn(run("MONTHLY:2026-09", TmdbSyncRunType.MONTHLY, date));

        BatchJobExecutionRes result = facade.triggerMonthlyReconcile(month);

        assertThat(result.jobName()).isEqualTo("MONTHLY:2026-09");
        verify(coordinator).startMonthly(month);
	}

	@Test
	@DisplayName("언어 분류는 콘텐츠를 갱신하지 않는 별도 업무 키로 실행")
	void triggerLanguageClassification() {
		YearMonth month = YearMonth.of(2026, 9);
		LocalDate date = month.atDay(1);
		when(coordinator.startClassification(month)).thenReturn(
			run("CLASSIFY_ONLY:2026-09", TmdbSyncRunType.CLASSIFY_ONLY, date)
		);

		BatchJobExecutionRes result = facade.triggerLanguageClassification(month);

		assertThat(result.jobName()).isEqualTo("CLASSIFY_ONLY:2026-09");
		verify(coordinator).startClassification(month);
	}

    @Test
    @DisplayName("기존 OTT 호환 API도 일간 coordinator를 호출")
    void legacyOttEndpointUsesCoordinator() {
		LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        when(coordinator.startDaily(today)).thenReturn(run("DAILY:" + today, TmdbSyncRunType.DAILY, today));

        facade.triggerOtt(MediaType.TV);

        verify(coordinator).startDaily(today);
    }

    private TmdbSyncRun run(String runKey, TmdbSyncRunType type, LocalDate date) {
        return new TmdbSyncRun(
            1L, runKey, type, date, TmdbSyncRunStatus.QUEUED, null, null, null, null,
            0, 4, null, LocalDateTime.of(2026, 9, 13, 0, 0), LocalDateTime.of(2026, 9, 13, 0, 0)
        );
    }
}
