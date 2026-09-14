package kr.flint.batch.job.tv;

import java.io.IOException;
import java.time.LocalDate;
import java.util.concurrent.Future;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.integration.async.AsyncItemProcessor;
import org.springframework.batch.integration.async.AsyncItemWriter;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import feign.FeignException;
import kr.flint.batch.config.BatchProperties;
import kr.flint.batch.config.TmdbBatchAsyncConfig;
import kr.flint.batch.config.TmdbRetryPolicyFactory;
import kr.flint.batch.download.TmdbExportDownloader;
import kr.flint.batch.job.ContentUpsertWriter;
import kr.flint.batch.job.ContentSyncDraft;
import kr.flint.batch.job.TmdbBatchSkipListener;
import kr.flint.batch.job.TmdbIdLine;
import kr.flint.batch.job.TmdbExportRegistryItemReader;
import kr.flint.batch.repository.TmdbCatalogEntryJdbcRepository;
import kr.flint.content.domain.MediaType;
import kr.flint.infra.tmdb.client.TmdbClient;
import kr.flint.batch.service.TmdbLocalizedTitleService;
import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class TmdbTvImportJobConfig {

	public static final String JOB_NAME = "tmdbTvImportJob";
	public static final String STEP_NAME = "tmdbTvImportStep";

	private final JobRepository jobRepository;
	private final PlatformTransactionManager transactionManager;
	private final TmdbExportDownloader downloader;
	private final TmdbClient tmdbClient;
	private final ContentUpsertWriter contentUpsertWriter;
	private final BatchProperties batchProperties;
	private final TmdbRetryPolicyFactory tmdbRetryPolicyFactory;
	private final TmdbLocalizedTitleService localizedTitleService;
	private final TmdbCatalogEntryJdbcRepository catalogEntryRepository;

	@Autowired
	@Qualifier(TmdbBatchAsyncConfig.TMDB_TASK_EXECUTOR)
	private TaskExecutor tmdbTaskExecutor;

	@Bean(name = JOB_NAME)
	public Job tmdbTvImportJob(@Qualifier(STEP_NAME) Step tmdbTvImportStep) {
		return new JobBuilder(JOB_NAME, jobRepository)
			.start(tmdbTvImportStep)
			.build();
	}

	@Bean(name = STEP_NAME)
	public Step tmdbTvImportStep(
		@Qualifier("tvIdsReader") ItemStreamReader<TmdbIdLine> tvIdsReader,
		@Qualifier("asyncTvProcessor") AsyncItemProcessor<TmdbIdLine, ContentSyncDraft> asyncTvProcessor,
		@Qualifier("asyncTvWriter") AsyncItemWriter<ContentSyncDraft> asyncTvWriter
	) {
		return new StepBuilder(STEP_NAME, jobRepository)
			.<TmdbIdLine, Future<ContentSyncDraft>>chunk(batchProperties.tmdb().chunkSize(), transactionManager)
			.reader(tvIdsReader)
			.processor(asyncTvProcessor)
			.writer(asyncTvWriter)
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
	public ItemStreamReader<TmdbIdLine> tvIdsReader(
		@Value("#{jobParameters['exportDate']}") String exportDate
	) {
		LocalDate date = (exportDate == null || exportDate.isBlank())
			? LocalDate.now().minusDays(1)
			: LocalDate.parse(exportDate);
		Resource resource;
		try {
			resource = downloader.fetchAsLineResource(TmdbExportDownloader.ExportType.TV, date);
		} catch (IOException | InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Failed to fetch TMDB tv export for " + date, e);
		}
		return new TmdbExportRegistryItemReader(
			resource,
			MediaType.TV,
			date,
			batchProperties.tmdb().chunkSize(),
			catalogEntryRepository
		);
	}

	@Bean
	@StepScope
	public AsyncItemProcessor<TmdbIdLine, ContentSyncDraft> asyncTvProcessor(
		@Value("#{jobParameters['classifyOnly']}") String classifyOnly
	) {
		AsyncItemProcessor<TmdbIdLine, ContentSyncDraft> async = new AsyncItemProcessor<>();
		ItemProcessor<TmdbIdLine, ContentSyncDraft> detailProcessor = tvDetailProcessorDelegate();
		async.setDelegate(item -> applyMode(detailProcessor.process(item), classifyOnly));
		async.setTaskExecutor(tmdbTaskExecutor);
		return async;
	}

	@Bean
	public ItemProcessor<TmdbIdLine, ContentSyncDraft> tvDetailProcessorDelegate() {
		return new TmdbTvDetailProcessor(tmdbClient, localizedTitleService);
	}

	@Bean
	public AsyncItemWriter<ContentSyncDraft> asyncTvWriter() {
		AsyncItemWriter<ContentSyncDraft> writer = new AsyncItemWriter<>();
		writer.setDelegate(contentUpsertWriter);
		return writer;
	}

	private ContentSyncDraft applyMode(ContentSyncDraft draft, String classifyOnly) {
		return draft != null && Boolean.parseBoolean(classifyOnly) ? draft.classificationOnly() : draft;
	}
}
