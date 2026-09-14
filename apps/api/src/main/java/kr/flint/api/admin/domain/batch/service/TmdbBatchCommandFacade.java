package kr.flint.api.admin.domain.batch.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;

import kr.flint.api.admin.domain.batch.dto.response.BatchJobExecutionRes;
import kr.flint.api.admin.domain.batch.dto.response.TmdbSyncRunRes;
import kr.flint.batch.sync.TmdbCatalogCoordinator;
import kr.flint.content.domain.MediaType;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TmdbBatchCommandFacade {
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final TmdbCatalogCoordinator coordinator;

    public BatchJobExecutionRes triggerDailySync(LocalDate businessDate) {
        LocalDate date = businessDate == null ? LocalDate.now(KST) : businessDate;
        return BatchJobExecutionRes.from(coordinator.startDaily(date));
    }

	public BatchJobExecutionRes triggerMonthlyReconcile(YearMonth businessMonth) {
        YearMonth month = businessMonth == null ? YearMonth.now(KST) : businessMonth;
        return BatchJobExecutionRes.from(coordinator.startMonthly(month));
	}

	public BatchJobExecutionRes triggerLanguageClassification(YearMonth businessMonth) {
		YearMonth month = businessMonth == null ? YearMonth.now(KST) : businessMonth;
		return BatchJobExecutionRes.from(coordinator.startClassification(month));
	}

    public List<TmdbSyncRunRes> getRuns(int limit) {
        return coordinator.findRecent(limit).stream().map(TmdbSyncRunRes::from).toList();
    }

    public TmdbSyncRunRes getRun(Long runId) {
        return TmdbSyncRunRes.from(coordinator.get(runId));
    }

    public BatchJobExecutionRes triggerMovies(String date) {
        LocalDate exportDate = date == null || date.isBlank() ? LocalDate.now(KST).minusDays(1) : LocalDate.parse(date);
        return triggerMonthlyReconcile(YearMonth.from(exportDate.plusDays(1)));
    }

    public BatchJobExecutionRes triggerTv(String date) {
        return triggerMovies(date);
    }

    public BatchJobExecutionRes triggerOtt(MediaType mediaType) {
        return triggerDailySync(LocalDate.now(KST));
    }

    public BatchJobExecutionRes triggerDelta(MediaType mediaType, String startDate, String endDate) {
        LocalDate businessDate = endDate == null || endDate.isBlank() ? LocalDate.now(KST) : LocalDate.parse(endDate);
        return triggerDailySync(businessDate);
    }
}
