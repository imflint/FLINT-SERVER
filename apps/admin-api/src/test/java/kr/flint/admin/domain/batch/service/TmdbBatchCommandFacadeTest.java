package kr.flint.admin.domain.batch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;

import kr.flint.admin.domain.batch.dto.response.BatchJobExecutionRes;
import kr.flint.batch.job.delta.TmdbDailyDeltaJobConfig;
import kr.flint.batch.job.movie.TmdbMovieImportJobConfig;

@ExtendWith(MockitoExtension.class)
class TmdbBatchCommandFacadeTest {

	@Mock
	private JobLauncher asyncJobLauncher;

	@Mock
	private Job tmdbMovieImportJob;

	@Mock
	private Job tmdbTvImportJob;

	@Mock
	private Job tmdbOttSyncJob;

	@Mock
	private Job tmdbDailyDeltaJob;

	private TmdbBatchCommandFacade tmdbBatchCommandFacade;

	@BeforeEach
	void setUp() {
		tmdbBatchCommandFacade = new TmdbBatchCommandFacade(
			asyncJobLauncher,
			tmdbMovieImportJob,
			tmdbTvImportJob,
			tmdbOttSyncJob,
			tmdbDailyDeltaJob
		);
	}

	@Test
	@DisplayName("영화 import date가 없으면 전일 exportDate로 Job을 실행")
	void triggerMoviesWithDefaultDate() throws Exception {
		// given
		JobExecution execution = jobExecution(TmdbMovieImportJobConfig.JOB_NAME);
		when(asyncJobLauncher.run(eq(tmdbMovieImportJob), org.mockito.ArgumentMatchers.any(JobParameters.class)))
			.thenReturn(execution);
		ArgumentCaptor<JobParameters> paramsCaptor = ArgumentCaptor.forClass(JobParameters.class);

		// when
		BatchJobExecutionRes result = tmdbBatchCommandFacade.triggerMovies(null);

		// then
		verify(asyncJobLauncher).run(eq(tmdbMovieImportJob), paramsCaptor.capture());
		JobParameters params = paramsCaptor.getValue();
		assertThat(params.getString("exportDate")).isEqualTo(LocalDate.now().minusDays(1).toString());
		assertThat(params.getLong("triggeredAt")).isNotNull();
		assertThat(result.jobName()).isEqualTo(TmdbMovieImportJobConfig.JOB_NAME);
	}

	@Test
	@DisplayName("변경분 동기화는 mediaType을 대문자로 변환하고 빈 날짜 파라미터를 제외")
	void triggerDeltaUppercaseMediaTypeAndSkipBlankDate() throws Exception {
		// given
		JobExecution execution = jobExecution(TmdbDailyDeltaJobConfig.JOB_NAME);
		when(asyncJobLauncher.run(eq(tmdbDailyDeltaJob), org.mockito.ArgumentMatchers.any(JobParameters.class)))
			.thenReturn(execution);
		ArgumentCaptor<JobParameters> paramsCaptor = ArgumentCaptor.forClass(JobParameters.class);

		// when
		tmdbBatchCommandFacade.triggerDelta("tv", "2026-05-01", "");

		// then
		verify(asyncJobLauncher).run(eq(tmdbDailyDeltaJob), paramsCaptor.capture());
		JobParameters params = paramsCaptor.getValue();
		assertThat(params.getString("mediaType")).isEqualTo("TV");
		assertThat(params.getString("startDate")).isEqualTo("2026-05-01");
		assertThat(params.getString("endDate")).isNull();
	}

	private JobExecution jobExecution(String jobName) {
		JobExecution execution = mock(JobExecution.class);
		JobInstance jobInstance = mock(JobInstance.class);
		when(execution.getJobInstance()).thenReturn(jobInstance);
		when(jobInstance.getJobName()).thenReturn(jobName);
		when(execution.getId()).thenReturn(1L);
		when(execution.getStatus()).thenReturn(BatchStatus.STARTED);
		when(execution.getCreateTime()).thenReturn(LocalDateTime.of(2026, 5, 10, 12, 0));
		return execution;
	}
}
