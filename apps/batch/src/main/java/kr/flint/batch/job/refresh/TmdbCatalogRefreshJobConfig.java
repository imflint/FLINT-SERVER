package kr.flint.batch.job.refresh;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Future;

import javax.sql.DataSource;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.integration.async.AsyncItemProcessor;
import org.springframework.batch.integration.async.AsyncItemWriter;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import feign.FeignException;
import kr.flint.batch.config.BatchProperties;
import kr.flint.batch.config.TmdbBatchAsyncConfig;
import kr.flint.batch.config.TmdbRetryPolicyFactory;
import kr.flint.batch.job.ContentSyncDraft;
import kr.flint.batch.job.ContentUpsertWriter;
import kr.flint.batch.job.TmdbBatchSkipListener;
import kr.flint.batch.job.TmdbIdLine;
import kr.flint.batch.job.movie.TmdbMovieDetailProcessor;
import kr.flint.batch.job.tv.TmdbTvDetailProcessor;
import kr.flint.batch.service.TmdbLocalizedTitleService;
import kr.flint.content.domain.MediaType;
import kr.flint.infra.tmdb.client.TmdbClient;
import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class TmdbCatalogRefreshJobConfig {

	public static final String JOB_NAME = "tmdbCatalogRefreshJob";
	public static final String STEP_NAME = "tmdbCatalogRefreshStep";

	private final JobRepository jobRepository;
	private final PlatformTransactionManager transactionManager;
	private final DataSource dataSource;
	private final TmdbClient tmdbClient;
	private final TmdbLocalizedTitleService localizedTitleService;
	private final ContentUpsertWriter contentUpsertWriter;
	private final BatchProperties batchProperties;
	private final TmdbRetryPolicyFactory retryPolicyFactory;
	private final @Qualifier(TmdbBatchAsyncConfig.TMDB_TASK_EXECUTOR) TaskExecutor tmdbTaskExecutor;

	@Bean(name = JOB_NAME)
	public Job tmdbCatalogRefreshJob(@Qualifier(STEP_NAME) Step step) {
		return new JobBuilder(JOB_NAME, jobRepository).start(step).build();
	}

	@Bean(name = STEP_NAME)
	public Step tmdbCatalogRefreshStep(
		@Qualifier("tmdbCatalogRefreshReader") JdbcPagingItemReader<TmdbIdLine> reader,
		@Qualifier("asyncCatalogRefreshProcessor") AsyncItemProcessor<TmdbIdLine, ContentSyncDraft> processor,
		@Qualifier("asyncCatalogRefreshWriter") AsyncItemWriter<ContentSyncDraft> writer
	) {
		return new StepBuilder(STEP_NAME, jobRepository)
			.<TmdbIdLine, Future<ContentSyncDraft>>chunk(batchProperties.tmdb().chunkSize(), transactionManager)
			.reader(reader)
			.processor(processor)
			.writer(writer)
			.faultTolerant()
			.retry(FeignException.class)
			.noRetry(FeignException.NotFound.class)
			.retryLimit(batchProperties.tmdb().retryAttempts())
			.backOffPolicy(retryPolicyFactory.fixedBackOffPolicy())
			.skip(FeignException.NotFound.class)
			.skipLimit(Integer.MAX_VALUE)
			.listener(new TmdbBatchSkipListener())
			.build();
	}

	@Bean
	@StepScope
	public JdbcPagingItemReader<TmdbIdLine> tmdbCatalogRefreshReader(
		@Value("#{jobParameters['mediaType']}") String mediaType
	) {
		MediaType type = parseMediaType(mediaType);
		return new JdbcPagingItemReaderBuilder<TmdbIdLine>()
			.name("tmdbCatalogRefreshReader-" + type.name().toLowerCase(Locale.ROOT))
			.dataSource(dataSource)
			.selectClause("SELECT id, tmdb_id")
			.fromClause("FROM tmdb_catalog_entry")
			.whereClause("""
				WHERE media_type = :mediaType
				  AND (status = 'RETRY' OR (status = 'SYNCED' AND next_refresh_at <= UTC_TIMESTAMP()))
				""")
			.sortKeys(Map.of("id", Order.ASCENDING))
			.parameterValues(Map.of("mediaType", type.name()))
			.rowMapper((resultSet, rowNumber) -> new TmdbIdLine(
				resultSet.getLong("tmdb_id"), null, null, null
			))
			.pageSize(batchProperties.tmdb().chunkSize())
			.build();
	}

	@Bean
	@StepScope
	public AsyncItemProcessor<TmdbIdLine, ContentSyncDraft> asyncCatalogRefreshProcessor(
		@Value("#{jobParameters['mediaType']}") String mediaType
	) {
		MediaType type = parseMediaType(mediaType);
		ItemProcessor<TmdbIdLine, ContentSyncDraft> delegate = type == MediaType.TV
			? new TmdbTvDetailProcessor(tmdbClient, localizedTitleService)
			: new TmdbMovieDetailProcessor(tmdbClient, localizedTitleService);
		AsyncItemProcessor<TmdbIdLine, ContentSyncDraft> processor = new AsyncItemProcessor<>();
		processor.setDelegate(delegate);
		processor.setTaskExecutor(tmdbTaskExecutor);
		return processor;
	}

	@Bean
	public AsyncItemWriter<ContentSyncDraft> asyncCatalogRefreshWriter() {
		AsyncItemWriter<ContentSyncDraft> writer = new AsyncItemWriter<>();
		writer.setDelegate(contentUpsertWriter);
		return writer;
	}

	private MediaType parseMediaType(String mediaType) {
		return mediaType == null || mediaType.isBlank()
			? MediaType.MOVIE
			: MediaType.valueOf(mediaType.toUpperCase(Locale.ROOT));
	}
}
