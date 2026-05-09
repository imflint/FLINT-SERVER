package kr.flint.batch.job.delta;

import java.time.LocalDate;
import java.util.concurrent.Future;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.integration.async.AsyncItemProcessor;
import org.springframework.batch.integration.async.AsyncItemWriter;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import feign.FeignException;
import kr.flint.batch.config.BatchProperties;
import kr.flint.batch.config.TmdbBatchAsyncConfig;
import kr.flint.batch.job.ContentUpsertWriter;
import kr.flint.batch.job.TmdbBatchSkipListener;
import kr.flint.batch.job.TmdbIdLine;
import kr.flint.batch.job.movie.TmdbMovieDetailProcessor;
import kr.flint.batch.job.tv.TmdbTvDetailProcessor;
import kr.flint.content.domain.MediaType;
import kr.flint.content.dto.ContentUpsertCommand;
import kr.flint.infra.tmdb.client.TmdbClient;
import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class TmdbDailyDeltaJobConfig {

	public static final String JOB_NAME = "tmdbDailyDeltaJob";
	public static final String STEP_NAME = "tmdbDailyDeltaStep";

	private final JobRepository jobRepository;
	private final PlatformTransactionManager transactionManager;
	private final TmdbClient tmdbClient;
	private final ContentUpsertWriter contentUpsertWriter;
	private final BatchProperties batchProperties;

	@Autowired
	@Qualifier(TmdbBatchAsyncConfig.TMDB_TASK_EXECUTOR)
	private TaskExecutor tmdbTaskExecutor;

	@Bean(name = JOB_NAME)
	public Job tmdbDailyDeltaJob() {
		return new JobBuilder(JOB_NAME, jobRepository)
			.start(tmdbDailyDeltaStep())
			.build();
	}

	@Bean
	public Step tmdbDailyDeltaStep() {
		return new StepBuilder(STEP_NAME, jobRepository)
			.<TmdbIdLine, Future<ContentUpsertCommand>>chunk(batchProperties.tmdb().chunkSize(), transactionManager)
			.reader(deltaReader(null, null, null))
			.processor(asyncDeltaProcessor(null))
			.writer(asyncDeltaWriter())
			.faultTolerant()
			.retry(FeignException.class)
			.noRetry(FeignException.NotFound.class)
			.retryLimit(batchProperties.tmdb().retryAttempts())
			.skip(FeignException.NotFound.class)
			.skipLimit(Integer.MAX_VALUE)
			.listener(new TmdbBatchSkipListener())
			.build();
	}

	@Bean
	@Scope("job")
	public ItemReader<TmdbIdLine> deltaReader(
		@Value("#{jobParameters['mediaType']}") String mediaType,
		@Value("#{jobParameters['startDate']}") String startDate,
		@Value("#{jobParameters['endDate']}") String endDate
	) {
		MediaType type = (mediaType == null || mediaType.isBlank())
			? MediaType.MOVIE
			: MediaType.valueOf(mediaType.toUpperCase());
		String start = (startDate == null || startDate.isBlank())
			? LocalDate.now().minusDays(1).toString()
			: startDate;
		String end = (endDate == null || endDate.isBlank())
			? LocalDate.now().toString()
			: endDate;
		return new TmdbChangesItemReader(tmdbClient, type, start, end);
	}

	@Bean
	@Scope("job")
	public AsyncItemProcessor<TmdbIdLine, ContentUpsertCommand> asyncDeltaProcessor(
		@Value("#{jobParameters['mediaType']}") String mediaType
	) {
		MediaType type = (mediaType == null || mediaType.isBlank())
			? MediaType.MOVIE
			: MediaType.valueOf(mediaType.toUpperCase());
		ItemProcessor<TmdbIdLine, ContentUpsertCommand> delegate = type == MediaType.TV
			? new TmdbTvDetailProcessor(tmdbClient)
			: new TmdbMovieDetailProcessor(tmdbClient);
		AsyncItemProcessor<TmdbIdLine, ContentUpsertCommand> async = new AsyncItemProcessor<>();
		async.setDelegate(delegate);
		async.setTaskExecutor(tmdbTaskExecutor);
		return async;
	}

	@Bean
	public AsyncItemWriter<ContentUpsertCommand> asyncDeltaWriter() {
		AsyncItemWriter<ContentUpsertCommand> writer = new AsyncItemWriter<>();
		writer.setDelegate(contentUpsertWriter);
		return writer;
	}
}
