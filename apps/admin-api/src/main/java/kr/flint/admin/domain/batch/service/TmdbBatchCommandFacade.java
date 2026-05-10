package kr.flint.admin.domain.batch.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import kr.flint.admin.domain.batch.dto.response.BatchJobExecutionRes;
import kr.flint.batch.job.delta.TmdbDailyDeltaJobConfig;
import kr.flint.batch.job.movie.TmdbMovieImportJobConfig;
import kr.flint.batch.job.ott.TmdbOttSyncJobConfig;
import kr.flint.batch.job.tv.TmdbTvImportJobConfig;

@Service
public class TmdbBatchCommandFacade {

	private final JobLauncher asyncJobLauncher;
	private final Job tmdbMovieImportJob;
	private final Job tmdbTvImportJob;
	private final Job tmdbOttSyncJob;
	private final Job tmdbDailyDeltaJob;

	public TmdbBatchCommandFacade(
		@Qualifier("asyncJobLauncher") JobLauncher asyncJobLauncher,
		@Qualifier(TmdbMovieImportJobConfig.JOB_NAME) Job tmdbMovieImportJob,
		@Qualifier(TmdbTvImportJobConfig.JOB_NAME) Job tmdbTvImportJob,
		@Qualifier(TmdbOttSyncJobConfig.JOB_NAME) Job tmdbOttSyncJob,
		@Qualifier(TmdbDailyDeltaJobConfig.JOB_NAME) Job tmdbDailyDeltaJob
	) {
		this.asyncJobLauncher = asyncJobLauncher;
		this.tmdbMovieImportJob = tmdbMovieImportJob;
		this.tmdbTvImportJob = tmdbTvImportJob;
		this.tmdbOttSyncJob = tmdbOttSyncJob;
		this.tmdbDailyDeltaJob = tmdbDailyDeltaJob;
	}

	public BatchJobExecutionRes triggerMovies(String date) throws Exception {
		JobParameters params = baseParams()
			.addString("exportDate", resolveExportDate(date))
			.toJobParameters();
		return BatchJobExecutionRes.from(asyncJobLauncher.run(tmdbMovieImportJob, params));
	}

	public BatchJobExecutionRes triggerTv(String date) throws Exception {
		JobParameters params = baseParams()
			.addString("exportDate", resolveExportDate(date))
			.toJobParameters();
		return BatchJobExecutionRes.from(asyncJobLauncher.run(tmdbTvImportJob, params));
	}

	public BatchJobExecutionRes triggerOtt(String mediaType) throws Exception {
		JobParameters params = baseParams()
			.addString("mediaType", mediaType.toUpperCase(Locale.ROOT))
			.toJobParameters();
		return BatchJobExecutionRes.from(asyncJobLauncher.run(tmdbOttSyncJob, params));
	}

	public BatchJobExecutionRes triggerDelta(String mediaType, String startDate, String endDate) throws Exception {
		JobParametersBuilder builder = baseParams()
			.addString("mediaType", mediaType.toUpperCase(Locale.ROOT));
		addIfPresent(builder, "startDate", startDate);
		addIfPresent(builder, "endDate", endDate);
		return BatchJobExecutionRes.from(asyncJobLauncher.run(tmdbDailyDeltaJob, builder.toJobParameters()));
	}

	private String resolveExportDate(String date) {
		if (date == null || date.isBlank()) {
			return LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
		}
		return date;
	}

	private void addIfPresent(JobParametersBuilder builder, String name, String value) {
		if (value != null && !value.isBlank()) {
			builder.addString(name, value);
		}
	}

	private JobParametersBuilder baseParams() {
		return new JobParametersBuilder()
			.addLong("triggeredAt", System.currentTimeMillis());
	}
}
