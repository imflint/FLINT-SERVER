package kr.flint.batch.sync;

import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import kr.flint.batch.job.delta.TmdbDailyDeltaJobConfig;
import kr.flint.batch.job.movie.TmdbMovieImportJobConfig;
import kr.flint.batch.job.ott.TmdbOttSyncJobConfig;
import kr.flint.batch.job.refresh.TmdbCatalogRefreshJobConfig;
import kr.flint.batch.job.tv.TmdbTvImportJobConfig;
import kr.flint.batch.repository.TmdbSyncRunJdbcRepository;
import kr.flint.batch.repository.TmdbSyncRunJdbcRepository.PreparedRun;
import kr.flint.batch.service.TmdbOttProviderMasterService;
import kr.flint.batch.service.TmdbChangeWindowService;
import kr.flint.shared.exception.ErrorCode;
import kr.flint.shared.exception.GeneralException;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class TmdbCatalogCoordinator {

    private static final Duration LEASE_DURATION = Duration.ofMinutes(2);
    private static final Duration STOP_TIMEOUT = Duration.ofMinutes(5);

    private final TmdbSyncRunJdbcRepository runRepository;
    private final JobLauncher asyncJobLauncher;
    private final JobExplorer jobExplorer;
    private final JobOperator jobOperator;
    private final TaskExecutor workflowExecutor;
    private final Job movieImportJob;
    private final Job tvImportJob;
    private final Job ottSyncJob;
	private final Job dailyDeltaJob;
	private final Job catalogRefreshJob;
	private final TmdbOttProviderMasterService providerMasterService;
	private final TmdbChangeWindowService changeWindowService;
    private final String ownerId = UUID.randomUUID().toString();
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    public TmdbCatalogCoordinator(
        TmdbSyncRunJdbcRepository runRepository,
        @Qualifier("asyncJobLauncher") JobLauncher asyncJobLauncher,
        JobExplorer jobExplorer,
        JobOperator jobOperator,
        @Qualifier("catalogWorkflowExecutor") TaskExecutor workflowExecutor,
        @Qualifier(TmdbMovieImportJobConfig.JOB_NAME) Job movieImportJob,
        @Qualifier(TmdbTvImportJobConfig.JOB_NAME) Job tvImportJob,
		@Qualifier(TmdbOttSyncJobConfig.JOB_NAME) Job ottSyncJob,
		@Qualifier(TmdbDailyDeltaJobConfig.JOB_NAME) Job dailyDeltaJob,
		@Qualifier(TmdbCatalogRefreshJobConfig.JOB_NAME) Job catalogRefreshJob,
		TmdbOttProviderMasterService providerMasterService,
		TmdbChangeWindowService changeWindowService
    ) {
        this.runRepository = runRepository;
        this.asyncJobLauncher = asyncJobLauncher;
        this.jobExplorer = jobExplorer;
        this.jobOperator = jobOperator;
        this.workflowExecutor = workflowExecutor;
        this.movieImportJob = movieImportJob;
        this.tvImportJob = tvImportJob;
		this.ottSyncJob = ottSyncJob;
		this.dailyDeltaJob = dailyDeltaJob;
		this.catalogRefreshJob = catalogRefreshJob;
		this.providerMasterService = providerMasterService;
		this.changeWindowService = changeWindowService;
    }

    public TmdbSyncRun startDaily(LocalDate businessDate) {
        String runKey = "DAILY:" + businessDate;
        return start(runKey, TmdbSyncRunType.DAILY, businessDate);
    }

	public TmdbSyncRun startMonthly(YearMonth businessMonth) {
        LocalDate businessDate = businessMonth.atDay(1);
        String runKey = "MONTHLY:" + businessMonth;
        return start(runKey, TmdbSyncRunType.MONTHLY, businessDate);
	}

	public TmdbSyncRun startClassification(YearMonth businessMonth) {
		LocalDate businessDate = businessMonth.atDay(1);
		String runKey = "CLASSIFY_ONLY:" + businessMonth;
		return start(runKey, TmdbSyncRunType.CLASSIFY_ONLY, businessDate);
	}

	public List<TmdbSyncRun> findRecent(int limit) {
		ensureSchemaReady();
		int safeLimit = Math.max(1, Math.min(limit, 100));
        return runRepository.findRecent(safeLimit);
    }

	public TmdbSyncRun get(Long runId) {
		ensureSchemaReady();
		return runRepository.findById(runId)
			.orElseThrow(() -> new GeneralException(ErrorCode.NOT_FOUND));
	}

    @Scheduled(fixedDelay = 30_000)
	public void heartbeat() {
		if (!shuttingDown.get() && runRepository.schemaReady()) {
            runRepository.heartbeat(ownerId, LEASE_DURATION);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
	public void resumeInterruptedRuns() {
		if (!runRepository.schemaReady()) {
			log.info("TMDB coordinator resume is disabled until manual DDL is applied");
			return;
		}
		for (TmdbSyncRun run : runRepository.findRestartable()) {
            try {
                start(run.runKey(), run.runType(), run.businessDate());
            } catch (RuntimeException exception) {
                log.warn("TMDB run resume deferred runKey={} cause={}", run.runKey(), exception.getMessage());
            }
        }
    }

    @EventListener(ContextClosedEvent.class)
	public void stopRunningJobs() {
		shuttingDown.set(true);
		if (!runRepository.schemaReady()) {
			return;
		}
        runRepository.markStoppingByOwner(ownerId);
        requestStop(TmdbMovieImportJobConfig.JOB_NAME);
        requestStop(TmdbTvImportJobConfig.JOB_NAME);
		requestStop(TmdbOttSyncJobConfig.JOB_NAME);
		requestStop(TmdbDailyDeltaJobConfig.JOB_NAME);
		requestStop(TmdbCatalogRefreshJobConfig.JOB_NAME);
        waitForJobsToStop();
        runRepository.markStoppedByOwner(ownerId);
    }

    private TmdbSyncRun start(
        String runKey,
        TmdbSyncRunType runType,
        LocalDate businessDate
	) {
		ensureSchemaReady();
		PreparedRun prepared = runRepository.prepare(runKey, runType, businessDate, ownerId, LEASE_DURATION);
        if (prepared.launch()) {
            workflowExecutor.execute(() -> executeWorkflow(prepared.run()));
        }
        return prepared.run();
    }

	private void executeWorkflow(TmdbSyncRun run) {
		long completedSteps = 0;
		List<JobInvocation> invocations = invocations(run);
		long totalSteps = invocations.size() + 1L;
		try {
			providerMasterService.synchronize();
			completedSteps++;
			runRepository.markRunning(run.runKey(), null, completedSteps, totalSteps);
			for (JobInvocation invocation : invocations) {
                if (shuttingDown.get()) {
                    runRepository.markStoppedByOwner(ownerId);
                    return;
                }
                JobExecution execution;
                try {
                    execution = launchAndWait(
                        invocation.job(),
						invocation.parameters(),
						run.runKey(),
						completedSteps,
						totalSteps
                    );
                } catch (JobInstanceAlreadyCompleteException ignored) {
                    completedSteps++;
                    continue;
                }
                if (execution.getStatus() == BatchStatus.STOPPED) {
                    runRepository.markStoppedByOwner(ownerId);
                    return;
                }
                if (execution.getStatus() != BatchStatus.COMPLETED) {
                    throw new IllegalStateException("TMDB job did not complete: " + execution.getStatus());
                }
                completedSteps++;
				runRepository.markRunning(run.runKey(), execution.getId(), completedSteps, totalSteps);
			}
			runRepository.markCompleted(run.runKey(), totalSteps, totalSteps);
        } catch (Exception exception) {
            log.error("TMDB workflow failed runKey={}", run.runKey(), exception);
            runRepository.markFailed(run.runKey(), exception);
        }
    }

    private JobExecution launchAndWait(
        Job job,
		JobParameters parameters,
		String runKey,
		long completedSteps,
		long totalSteps
    ) throws Exception {
        JobExecution execution = asyncJobLauncher.run(job, parameters);
		runRepository.markRunning(runKey, execution.getId(), completedSteps, totalSteps);
        while (execution.getStatus().isRunning()) {
            Thread.sleep(2_000);
            JobExecution refreshed = jobExplorer.getJobExecution(execution.getId());
            if (refreshed != null) {
                execution = refreshed;
            }
        }
        return execution;
    }

    private List<JobInvocation> invocations(TmdbSyncRun run) {
		if (run.runType() == TmdbSyncRunType.MONTHLY || run.runType() == TmdbSyncRunType.CLASSIFY_ONLY) {
			String exportDate = run.businessDate().minusDays(1).toString();
			String classifyOnly = Boolean.toString(run.runType() == TmdbSyncRunType.CLASSIFY_ONLY);
			return List.of(
				new JobInvocation(movieImportJob, baseParameters(run).addString("exportDate", exportDate).addString("classifyOnly", classifyOnly).toJobParameters()),
				new JobInvocation(tvImportJob, baseParameters(run).addString("exportDate", exportDate).addString("classifyOnly", classifyOnly).toJobParameters())
            );
        }

		LocalDate startDate = runRepository.findLatestCompletedDailyDateBefore(run.businessDate())
			.orElse(run.businessDate().minusDays(1));
		List<JobInvocation> result = new ArrayList<>();
		for (TmdbChangeWindowService.DateWindow window : changeWindowService.split(startDate, run.businessDate())) {
			for (String mediaType : List.of("MOVIE", "TV")) {
				result.add(new JobInvocation(
					dailyDeltaJob,
					baseParameters(run)
						.addString("mediaType", mediaType)
						.addString("startDate", window.startDate().toString())
						.addString("endDate", window.endDate().toString())
						.toJobParameters()
				));
			}
		}
		result.add(new JobInvocation(
			catalogRefreshJob,
			baseParameters(run).addString("mediaType", "MOVIE").toJobParameters()
		));
		result.add(new JobInvocation(
			catalogRefreshJob,
			baseParameters(run).addString("mediaType", "TV").toJobParameters()
		));
		return List.copyOf(result);
    }

	private JobParametersBuilder baseParameters(TmdbSyncRun run) {
        return new JobParametersBuilder()
            .addString("runKey", run.runKey())
            .addString("businessDate", run.businessDate().toString());
	}

	private void ensureSchemaReady() {
		if (!runRepository.schemaReady()) {
			throw new GeneralException(ErrorCode.CONFLICT, "TMDB coordinator DDL이 적용되지 않았습니다.");
		}
	}

    private void requestStop(String jobName) {
        try {
            Set<Long> executions = jobOperator.getRunningExecutions(jobName);
            for (Long executionId : executions) {
                jobOperator.stop(executionId);
            }
        } catch (Exception exception) {
            log.warn("Failed to request batch stop jobName={} cause={}", jobName, exception.getMessage());
        }
    }

    private void waitForJobsToStop() {
        long deadline = System.nanoTime() + STOP_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            boolean running = List.of(
                    TmdbMovieImportJobConfig.JOB_NAME,
                    TmdbTvImportJobConfig.JOB_NAME,
					TmdbOttSyncJobConfig.JOB_NAME,
					TmdbDailyDeltaJobConfig.JOB_NAME,
					TmdbCatalogRefreshJobConfig.JOB_NAME
                ).stream()
                .anyMatch(this::hasRunningExecution);
            if (!running) {
                return;
            }
            try {
                Thread.sleep(2_000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        log.warn("Timed out waiting for TMDB jobs to stop after {} seconds", STOP_TIMEOUT.toSeconds());
    }

    private boolean hasRunningExecution(String jobName) {
        try {
            return !jobOperator.getRunningExecutions(jobName).isEmpty();
        } catch (Exception exception) {
            return false;
        }
    }

    private record JobInvocation(Job job, JobParameters parameters) {
    }
}
