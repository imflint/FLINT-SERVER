package kr.flint.batch.scheduler;

import java.time.LocalDate;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import kr.flint.batch.job.delta.TmdbDailyDeltaJobConfig;
import kr.flint.batch.job.movie.TmdbMovieImportJobConfig;
import kr.flint.batch.job.ott.TmdbOttSyncJobConfig;
import kr.flint.batch.job.tv.TmdbTvImportJobConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// 운영 안정화 후 flint.batch.scheduling.enabled=true 로 켜는 placeholder.
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "flint.batch.scheduling.enabled", havingValue = "true")
@Slf4j
public class TmdbBatchScheduler {

	private final JobLauncher asyncJobLauncher;

	@Qualifier(TmdbMovieImportJobConfig.JOB_NAME)
	private final Job tmdbMovieImportJob;

	@Qualifier(TmdbTvImportJobConfig.JOB_NAME)
	private final Job tmdbTvImportJob;

	@Qualifier(TmdbOttSyncJobConfig.JOB_NAME)
	private final Job tmdbOttSyncJob;

	@Qualifier(TmdbDailyDeltaJobConfig.JOB_NAME)
	private final Job tmdbDailyDeltaJob;

	@Scheduled(cron = "0 0 5 * * *", zone = "Asia/Seoul")
	public void runMovieImport() throws Exception {
		log.info("[scheduled] tmdbMovieImportJob");
		asyncJobLauncher.run(tmdbMovieImportJob, exportDateParams());
	}

	@Scheduled(cron = "0 30 5 * * *", zone = "Asia/Seoul")
	public void runDelta() throws Exception {
		log.info("[scheduled] tmdbDailyDeltaJob movie");
		JobParameters params = new JobParametersBuilder()
			.addLong("triggeredAt", System.currentTimeMillis())
			.addString("mediaType", "MOVIE")
			.toJobParameters();
		asyncJobLauncher.run(tmdbDailyDeltaJob, params);
	}

	@Scheduled(cron = "0 0 6 * * *", zone = "Asia/Seoul")
	public void runTvImport() throws Exception {
		log.info("[scheduled] tmdbTvImportJob");
		asyncJobLauncher.run(tmdbTvImportJob, exportDateParams());
	}

	@Scheduled(cron = "0 30 6 * * *", zone = "Asia/Seoul")
	public void runOttSync() throws Exception {
		log.info("[scheduled] tmdbOttSyncJob movie");
		JobParameters params = new JobParametersBuilder()
			.addLong("triggeredAt", System.currentTimeMillis())
			.addString("mediaType", "MOVIE")
			.toJobParameters();
		asyncJobLauncher.run(tmdbOttSyncJob, params);
	}

	private JobParameters exportDateParams() {
		return new JobParametersBuilder()
			.addLong("triggeredAt", System.currentTimeMillis())
			.addString("exportDate", LocalDate.now().minusDays(1).toString())
			.toJobParameters();
	}
}
