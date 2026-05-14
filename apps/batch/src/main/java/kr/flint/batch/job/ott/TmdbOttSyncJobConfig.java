package kr.flint.batch.job.ott;

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
import org.springframework.beans.factory.annotation.Autowired;
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
import kr.flint.batch.job.TmdbBatchSkipListener;
import kr.flint.content.domain.MediaType;
import kr.flint.infra.tmdb.client.TmdbClient;
import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class TmdbOttSyncJobConfig {

	public static final String JOB_NAME = "tmdbOttSyncJob";
	public static final String STEP_NAME = "tmdbOttSyncStep";

	private final JobRepository jobRepository;
	private final PlatformTransactionManager transactionManager;
	private final DataSource dataSource;
	private final TmdbClient tmdbClient;
	private final OttSyncWriter ottSyncWriter;
	private final BatchProperties batchProperties;
	private final TmdbRetryPolicyFactory tmdbRetryPolicyFactory;

	@Autowired
	@Qualifier(TmdbBatchAsyncConfig.TMDB_TASK_EXECUTOR)
	private TaskExecutor tmdbTaskExecutor;

	@Bean(name = JOB_NAME)
	public Job tmdbOttSyncJob(@Qualifier(STEP_NAME) Step tmdbOttSyncStep) {
		return new JobBuilder(JOB_NAME, jobRepository)
			.start(tmdbOttSyncStep)
			.build();
	}

	@Bean(name = STEP_NAME)
	public Step tmdbOttSyncStep(
		@Qualifier("ottContentReader") JdbcPagingItemReader<OttSyncContentRow> ottContentReader,
		@Qualifier("asyncOttProcessor") AsyncItemProcessor<OttSyncContentRow, OttSyncDraft> asyncOttProcessor,
		@Qualifier("asyncOttWriter") AsyncItemWriter<OttSyncDraft> asyncOttWriter
	) {
		return new StepBuilder(STEP_NAME, jobRepository)
			.<OttSyncContentRow, Future<OttSyncDraft>>chunk(batchProperties.tmdb().chunkSize(), transactionManager)
			.reader(ottContentReader)
			.processor(asyncOttProcessor)
			.writer(asyncOttWriter)
			.faultTolerant()
			.retry(FeignException.class)
			.noRetry(FeignException.NotFound.class)
			.retryLimit(batchProperties.tmdb().retryAttempts())
			.backOffPolicy(tmdbRetryPolicyFactory.fixedBackOffPolicy())
			.skip(FeignException.NotFound.class)
			.skipLimit(Integer.MAX_VALUE)
			.listener(new TmdbBatchSkipListener())
			.build();
	}

	@Bean
	@StepScope
	public JdbcPagingItemReader<OttSyncContentRow> ottContentReader(
		@Value("#{jobParameters['mediaType']}") String mediaType
	) {
		MediaType type = (mediaType == null || mediaType.isBlank())
			? MediaType.MOVIE
			: MediaType.valueOf(mediaType.toUpperCase(Locale.ROOT));

		return new JdbcPagingItemReaderBuilder<OttSyncContentRow>()
			.name("ottContentReader-" + type.name().toLowerCase())
			.dataSource(dataSource)
			.selectClause("SELECT id, tmdb_id, media_type")
			.fromClause("FROM content")
			.whereClause("WHERE media_type = :mediaType")
			.sortKeys(Map.of("id", Order.ASCENDING))
			.parameterValues(Map.of("mediaType", type.name()))
			.rowMapper((rs, rowNum) -> new OttSyncContentRow(
				rs.getLong("id"),
				rs.getLong("tmdb_id"),
				MediaType.valueOf(rs.getString("media_type"))
			))
			.pageSize(batchProperties.tmdb().chunkSize())
			.build();
	}

	@Bean
	public AsyncItemProcessor<OttSyncContentRow, OttSyncDraft> asyncOttProcessor() {
		AsyncItemProcessor<OttSyncContentRow, OttSyncDraft> async = new AsyncItemProcessor<>();
		async.setDelegate(ottProvidersProcessorDelegate());
		async.setTaskExecutor(tmdbTaskExecutor);
		return async;
	}

	@Bean
	public ItemProcessor<OttSyncContentRow, OttSyncDraft> ottProvidersProcessorDelegate() {
		return new TmdbOttProvidersProcessor(tmdbClient);
	}

	@Bean
	public AsyncItemWriter<OttSyncDraft> asyncOttWriter() {
		AsyncItemWriter<OttSyncDraft> writer = new AsyncItemWriter<>();
		writer.setDelegate(ottSyncWriter);
		return writer;
	}
}
