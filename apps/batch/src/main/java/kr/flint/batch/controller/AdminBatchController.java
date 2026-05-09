package kr.flint.batch.controller;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import kr.flint.batch.job.delta.TmdbDailyDeltaJobConfig;
import kr.flint.batch.job.movie.TmdbMovieImportJobConfig;
import kr.flint.batch.job.ott.TmdbOttSyncJobConfig;
import kr.flint.batch.job.tv.TmdbTvImportJobConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/admin/batch")
@RequiredArgsConstructor
@Slf4j
public class AdminBatchController {

	private final JobLauncher asyncJobLauncher;

	@Qualifier(TmdbMovieImportJobConfig.JOB_NAME)
	private final Job tmdbMovieImportJob;

	@Qualifier(TmdbTvImportJobConfig.JOB_NAME)
	private final Job tmdbTvImportJob;

	@Qualifier(TmdbOttSyncJobConfig.JOB_NAME)
	private final Job tmdbOttSyncJob;

	@Qualifier(TmdbDailyDeltaJobConfig.JOB_NAME)
	private final Job tmdbDailyDeltaJob;

	@PostMapping("/movies")
	public Map<String, Object> triggerMovies(@RequestParam(required = false) String date) throws Exception {
		String exportDate = (date == null || date.isBlank())
			? LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
			: date;
		JobParameters params = baseParams()
			.addString("exportDate", exportDate)
			.toJobParameters();
		return executionResponse(asyncJobLauncher.run(tmdbMovieImportJob, params));
	}

	@PostMapping("/tv")
	public Map<String, Object> triggerTv(@RequestParam(required = false) String date) throws Exception {
		String exportDate = (date == null || date.isBlank())
			? LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
			: date;
		JobParameters params = baseParams()
			.addString("exportDate", exportDate)
			.toJobParameters();
		return executionResponse(asyncJobLauncher.run(tmdbTvImportJob, params));
	}

	@PostMapping("/ott")
	public Map<String, Object> triggerOtt(@RequestParam(defaultValue = "MOVIE") String mediaType) throws Exception {
		JobParameters params = baseParams()
			.addString("mediaType", mediaType.toUpperCase())
			.toJobParameters();
		return executionResponse(asyncJobLauncher.run(tmdbOttSyncJob, params));
	}

	@PostMapping("/delta")
	public Map<String, Object> triggerDelta(
		@RequestParam(defaultValue = "MOVIE") String mediaType,
		@RequestParam(required = false) String startDate,
		@RequestParam(required = false) String endDate
	) throws Exception {
		JobParametersBuilder builder = baseParams()
			.addString("mediaType", mediaType.toUpperCase());
		if (startDate != null && !startDate.isBlank()) {
			builder.addString("startDate", startDate);
		}
		if (endDate != null && !endDate.isBlank()) {
			builder.addString("endDate", endDate);
		}
		return executionResponse(asyncJobLauncher.run(tmdbDailyDeltaJob, builder.toJobParameters()));
	}

	private JobParametersBuilder baseParams() {
		return new JobParametersBuilder()
			.addLong("triggeredAt", System.currentTimeMillis());
	}

	private Map<String, Object> executionResponse(JobExecution execution) {
		return Map.of(
			"jobName", execution.getJobInstance().getJobName(),
			"executionId", execution.getId(),
			"status", execution.getStatus().name(),
			"createTime", String.valueOf(execution.getCreateTime())
		);
	}
}
