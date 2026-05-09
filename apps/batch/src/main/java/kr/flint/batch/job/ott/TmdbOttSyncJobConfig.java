package kr.flint.batch.job.ott;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.integration.async.AsyncItemProcessor;
import org.springframework.batch.integration.async.AsyncItemWriter;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.data.RepositoryItemReader;
import org.springframework.batch.item.data.builder.RepositoryItemReaderBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;

import feign.FeignException;
import kr.flint.batch.config.BatchProperties;
import kr.flint.batch.config.TmdbBatchAsyncConfig;
import kr.flint.batch.job.TmdbBatchSkipListener;
import kr.flint.content.domain.Content;
import kr.flint.content.domain.MediaType;
import kr.flint.content.repository.ContentRepository;
import kr.flint.infra.tmdb.client.TmdbClient;
import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class TmdbOttSyncJobConfig {

	public static final String JOB_NAME = "tmdbOttSyncJob";
	public static final String STEP_NAME = "tmdbOttSyncStep";

	private final JobRepository jobRepository;
	private final PlatformTransactionManager transactionManager;
	private final ContentRepository contentRepository;
	private final TmdbClient tmdbClient;
	private final OttSyncWriter ottSyncWriter;
	private final BatchProperties batchProperties;

	@Autowired
	@Qualifier(TmdbBatchAsyncConfig.TMDB_TASK_EXECUTOR)
	private TaskExecutor tmdbTaskExecutor;

	@Bean(name = JOB_NAME)
	public Job tmdbOttSyncJob() {
		return new JobBuilder(JOB_NAME, jobRepository)
			.start(tmdbOttSyncStep())
			.build();
	}

	@Bean
	public Step tmdbOttSyncStep() {
		return new StepBuilder(STEP_NAME, jobRepository)
			.<Content, Future<OttSyncDraft>>chunk(batchProperties.tmdb().chunkSize(), transactionManager)
			.reader(ottContentReader(null))
			.processor(asyncOttProcessor())
			.writer(asyncOttWriter())
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
	public RepositoryItemReader<Content> ottContentReader(
		@Value("#{jobParameters['mediaType']}") String mediaType
	) {
		MediaType type = (mediaType == null || mediaType.isBlank())
			? MediaType.MOVIE
			: MediaType.valueOf(mediaType.toUpperCase());

		Map<String, Sort.Direction> sorts = new HashMap<>();
		sorts.put("id", Sort.Direction.ASC);

		return new RepositoryItemReaderBuilder<Content>()
			.name("ottContentReader-" + type.name().toLowerCase())
			.repository(contentRepository)
			.methodName("findAllByMediaTypeOrderByIdAsc")
			.arguments(java.util.List.of(type))
			.pageSize(batchProperties.tmdb().chunkSize())
			.sorts(sorts)
			.build();
	}

	@Bean
	public AsyncItemProcessor<Content, OttSyncDraft> asyncOttProcessor() {
		AsyncItemProcessor<Content, OttSyncDraft> async = new AsyncItemProcessor<>();
		async.setDelegate(ottProvidersProcessorDelegate());
		async.setTaskExecutor(tmdbTaskExecutor);
		return async;
	}

	@Bean
	public ItemProcessor<Content, OttSyncDraft> ottProvidersProcessorDelegate() {
		return new TmdbOttProvidersProcessor(tmdbClient);
	}

	@Bean
	public AsyncItemWriter<OttSyncDraft> asyncOttWriter() {
		AsyncItemWriter<OttSyncDraft> writer = new AsyncItemWriter<>();
		writer.setDelegate(ottSyncWriter);
		return writer;
	}
}
