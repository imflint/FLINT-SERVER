package kr.flint.batch.sync;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import kr.flint.batch.job.movie.TmdbMovieImportJobConfig;
import kr.flint.batch.repository.TmdbSyncRunJdbcRepository;
import kr.flint.batch.repository.TmdbContentAdmissionJdbcRepository;
import kr.flint.batch.repository.TmdbSyncRunJdbcRepository.PreparedRun;
import kr.flint.batch.service.TmdbChangeWindowService;
import kr.flint.batch.service.TmdbOttProviderMasterService;
import kr.flint.shared.exception.GeneralException;

@ExtendWith(MockitoExtension.class)
class TmdbCatalogCoordinatorTest {

	@Mock
	private TmdbSyncRunJdbcRepository runRepository;
	@Mock
	private JobLauncher jobLauncher;
	@Mock
	private JobExplorer jobExplorer;
	@Mock
	private JobOperator jobOperator;
	@Mock
	private TaskExecutor workflowExecutor;
	@Mock
	private Job movieImportJob;
	@Mock
	private Job tvImportJob;
	@Mock
	private Job ottSyncJob;
	@Mock
	private Job dailyDeltaJob;
	@Mock
	private Job catalogRefreshJob;
	@Mock
	private TmdbOttProviderMasterService providerMasterService;
    @Mock
    private TmdbContentAdmissionJdbcRepository admissionRepository;

	private TmdbCatalogCoordinator coordinator;

	@BeforeEach
	void setUp() {
		coordinator = new TmdbCatalogCoordinator(
			runRepository,
			jobLauncher,
			jobExplorer,
			jobOperator,
			workflowExecutor,
			movieImportJob,
			tvImportJob,
			ottSyncJob,
			dailyDeltaJob,
			catalogRefreshJob,
			providerMasterService,
			new TmdbChangeWindowService(),
            admissionRepository
		);
	}

	@Test
	void resumesStoppedRunUsingTheSameStableBusinessKey() {
		TmdbSyncRun stopped = run(TmdbSyncRunStatus.STOPPED);
		when(runRepository.schemaReady()).thenReturn(true);
		when(runRepository.findRestartable()).thenReturn(List.of(stopped));
        when(admissionRepository.schemaReady()).thenReturn(true);
		when(runRepository.prepare(
			eq(stopped.runKey()),
			eq(stopped.runType()),
			eq(stopped.businessDate()),
			anyString(),
			any(Duration.class)
		)).thenReturn(new PreparedRun(stopped, false));

		coordinator.resumeInterruptedRuns();

		verify(runRepository).prepare(
			eq("DAILY:2026-09-13"),
			eq(TmdbSyncRunType.DAILY),
			eq(LocalDate.of(2026, 9, 13)),
			anyString(),
			any(Duration.class)
		);
	}

	@Test
	void shutdownRequestsRunningJobStopBeforeMarkingOwnedRunStopped() throws Exception {
		when(runRepository.schemaReady()).thenReturn(true);
		when(jobOperator.getRunningExecutions(TmdbMovieImportJobConfig.JOB_NAME))
			.thenReturn(Set.of(10L))
			.thenReturn(Set.of());

		coordinator.stopRunningJobs();

		InOrder order = org.mockito.Mockito.inOrder(runRepository, jobOperator);
		order.verify(runRepository).markStoppingByOwner(anyString());
		order.verify(jobOperator).stop(10L);
		order.verify(runRepository).markStoppedByOwner(anyString());
	}

    @Test
    void refusesToLaunchBeforeAdmissionDdlIsReady() {
        when(runRepository.schemaReady()).thenReturn(true);
        when(admissionRepository.schemaReady()).thenReturn(false);
        assertThatThrownBy(() -> coordinator.startDaily(LocalDate.of(2026, 10, 8)))
            .isInstanceOf(GeneralException.class);
        verify(runRepository, never()).prepare(anyString(), any(), any(), anyString(), any());
    }

    @Test
    void manualBackfillRejectsAllCatalogStartsBeforeAcquiringLease() {
        ReflectionTestUtils.setField(coordinator, "searchBackfillEnabled", true);
        for (Runnable trigger : new Runnable[] {
            () -> coordinator.startDaily(LocalDate.of(2026, 10, 9)),
            () -> coordinator.startMonthly(YearMonth.of(2026, 10)),
            () -> coordinator.startClassification(YearMonth.of(2026, 10))
        }) {
            assertThatThrownBy(trigger::run).isInstanceOf(GeneralException.class)
                .extracting(error -> ((GeneralException) error).getErrorCode())
                .isEqualTo(kr.flint.shared.exception.ErrorCode.CONFLICT);
        }
        verifyNoInteractions(runRepository, admissionRepository, jobLauncher, jobExplorer,
            jobOperator, workflowExecutor, providerMasterService);
    }

    @Test
    void manualBackfillDoesNotResumeHeartbeatOrStopOtherCatalogJobs() {
        ReflectionTestUtils.setField(coordinator, "searchBackfillEnabled", true);
        coordinator.resumeInterruptedRuns();
        coordinator.heartbeat();
        coordinator.stopRunningJobs();
        verifyNoInteractions(runRepository, admissionRepository, jobLauncher, jobExplorer,
            jobOperator, workflowExecutor, providerMasterService);
    }

	private TmdbSyncRun run(TmdbSyncRunStatus status) {
		return new TmdbSyncRun(
			1L,
			"DAILY:2026-09-13",
			TmdbSyncRunType.DAILY,
			LocalDate.of(2026, 9, 13),
			status,
			100L,
			"old-owner",
			LocalDateTime.of(2026, 9, 13, 0, 0),
			LocalDateTime.of(2026, 9, 13, 0, 0),
			1,
			4,
			null,
			LocalDateTime.of(2026, 9, 13, 0, 0),
			LocalDateTime.of(2026, 9, 13, 0, 0)
		);
	}
}
