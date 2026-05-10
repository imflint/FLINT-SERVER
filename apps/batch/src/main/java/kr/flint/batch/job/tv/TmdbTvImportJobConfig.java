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
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.JsonLineMapper;
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
import kr.flint.batch.download.TmdbExportDownloader;
import kr.flint.batch.job.ContentUpsertWriter;
import kr.flint.batch.job.TmdbBatchSkipListener;
import kr.flint.batch.job.TmdbIdLine;
import kr.flint.content.dto.ContentUpsertCommand;
import kr.flint.infra.tmdb.client.TmdbClient;
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
		@Qualifier("tvIdsReader") FlatFileItemReader<TmdbIdLine> tvIdsReader,
		@Qualifier("asyncTvProcessor") AsyncItemProcessor<TmdbIdLine, ContentUpsertCommand> asyncTvProcessor,
		@Qualifier("asyncTvWriter") AsyncItemWriter<ContentUpsertCommand> asyncTvWriter
	) {
		return new StepBuilder(STEP_NAME, jobRepository)
			.<TmdbIdLine, Future<ContentUpsertCommand>>chunk(batchProperties.tmdb().chunkSize(), transactionManager)
			.reader(tvIdsReader)
			.processor(asyncTvProcessor)
			.writer(asyncTvWriter)
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
	@StepScope
	public FlatFileItemReader<TmdbIdLine> tvIdsReader(
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
		JsonLineMapper jsonLineMapper = new JsonLineMapper();
		return new FlatFileItemReaderBuilder<TmdbIdLine>()
			.name("tvIdsReader")
			.resource(resource)
			.lineMapper((line, lineNumber) -> TmdbIdLine.fromMap(jsonLineMapper.mapLine(line, lineNumber)))
			.strict(true)
			.build();
	}

	@Bean
	public AsyncItemProcessor<TmdbIdLine, ContentUpsertCommand> asyncTvProcessor() {
		AsyncItemProcessor<TmdbIdLine, ContentUpsertCommand> async = new AsyncItemProcessor<>();
		async.setDelegate(tvDetailProcessorDelegate());
		async.setTaskExecutor(tmdbTaskExecutor);
		return async;
	}

	@Bean
	public ItemProcessor<TmdbIdLine, ContentUpsertCommand> tvDetailProcessorDelegate() {
		return new TmdbTvDetailProcessor(tmdbClient);
	}

	@Bean
	public AsyncItemWriter<ContentUpsertCommand> asyncTvWriter() {
		AsyncItemWriter<ContentUpsertCommand> writer = new AsyncItemWriter<>();
		writer.setDelegate(contentUpsertWriter);
		return writer;
	}
}
