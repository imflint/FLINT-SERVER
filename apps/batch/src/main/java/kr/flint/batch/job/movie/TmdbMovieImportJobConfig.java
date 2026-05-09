package kr.flint.batch.job.movie;

import java.io.IOException;
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
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.JsonLineMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
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
public class TmdbMovieImportJobConfig {

	public static final String JOB_NAME = "tmdbMovieImportJob";
	public static final String STEP_NAME = "tmdbMovieImportStep";

	private final JobRepository jobRepository;
	private final PlatformTransactionManager transactionManager;
	private final TmdbExportDownloader downloader;
	private final TmdbClient tmdbClient;
	private final ContentUpsertWriter contentUpsertWriter;
	private final BatchProperties batchProperties;

	@Bean(name = JOB_NAME)
	public Job tmdbMovieImportJob() {
		return new JobBuilder(JOB_NAME, jobRepository)
			.start(tmdbMovieImportStep())
			.build();
	}

	@Bean
	public Step tmdbMovieImportStep() {
		return new StepBuilder(STEP_NAME, jobRepository)
			.<TmdbIdLine, Future<ContentUpsertCommand>>chunk(batchProperties.tmdb().chunkSize(), transactionManager)
			.reader(movieIdsReader(null))
			.processor(asyncMovieProcessor())
			.writer(asyncContentWriter())
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
	public FlatFileItemReader<TmdbIdLine> movieIdsReader(
		@Value("#{jobParameters['exportDate']}") String exportDate
	) {
		LocalDate date = (exportDate == null || exportDate.isBlank())
			? LocalDate.now().minusDays(1)
			: LocalDate.parse(exportDate);
		Resource resource;
		try {
			resource = downloader.fetchAsLineResource(TmdbExportDownloader.ExportType.MOVIE, date);
		} catch (IOException | InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Failed to fetch TMDB movie export for " + date, e);
		}
		return new FlatFileItemReaderBuilder<TmdbIdLine>()
			.name("movieIdsReader")
			.resource(resource)
			.lineMapper(jsonLineToTmdbIdMapper())
			.strict(true)
			.build();
	}

	private org.springframework.batch.item.file.LineMapper<TmdbIdLine> jsonLineToTmdbIdMapper() {
		JsonLineMapper jsonLineMapper = new JsonLineMapper();
		return (line, lineNumber) -> {
			java.util.Map<String, Object> raw = jsonLineMapper.mapLine(line, lineNumber);
			return TmdbIdLine.fromMap(raw);
		};
	}

	@Bean
	public AsyncItemProcessor<TmdbIdLine, ContentUpsertCommand> asyncMovieProcessor() {
		AsyncItemProcessor<TmdbIdLine, ContentUpsertCommand> async = new AsyncItemProcessor<>();
		async.setDelegate(movieDetailProcessorDelegate());
		async.setTaskExecutor(tmdbTaskExecutor());
		return async;
	}

	@Bean
	public ItemProcessor<TmdbIdLine, ContentUpsertCommand> movieDetailProcessorDelegate() {
		return new TmdbMovieDetailProcessor(tmdbClient);
	}

	@Bean
	public AsyncItemWriter<ContentUpsertCommand> asyncContentWriter() {
		AsyncItemWriter<ContentUpsertCommand> writer = new AsyncItemWriter<>();
		writer.setDelegate(contentUpsertWriter);
		return writer;
	}

	private TaskExecutor tmdbTaskExecutor() {
		return tmdbTaskExecutorRef;
	}

	@org.springframework.beans.factory.annotation.Autowired
	@Qualifier(TmdbBatchAsyncConfig.TMDB_TASK_EXECUTOR)
	private TaskExecutor tmdbTaskExecutorRef;
}
