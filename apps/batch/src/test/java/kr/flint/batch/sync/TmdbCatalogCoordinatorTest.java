package kr.flint.batch.sync;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

import kr.flint.batch.job.movie.TmdbMovieImportJobConfig;
import kr.flint.batch.repository.TmdbSyncRunJdbcRepository;
import kr.flint.batch.repository.TmdbSyncRunJdbcRepository.PreparedRun;
import kr.flint.batch.service.TmdbChangeWindowService;
import kr.flint.batch.service.TmdbOttProviderMasterService;

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
			new TmdbChangeWindowService()
		);
	}

	@Test
	void resumesStoppedRunUsingTheSameStableBusinessKey() {
		TmdbSyncRun stopped = run(TmdbSyncRunStatus.STOPPED);
		when(runRepository.schemaReady()).thenReturn(true);
		when(runRepository.findRestartable()).thenReturn(List.of(stopped));
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
