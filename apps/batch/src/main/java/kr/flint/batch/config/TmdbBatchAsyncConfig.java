package kr.flint.batch.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

@Configuration
public class TmdbBatchAsyncConfig {

	public static final String TMDB_TASK_EXECUTOR = "tmdbTaskExecutor";
	private static final int DEFAULT_CONCURRENCY_LIMIT = 50;
	private static final long TASK_TERMINATION_TIMEOUT_MS = 60_000;

	private final BatchProperties batchProperties;

	public TmdbBatchAsyncConfig(BatchProperties batchProperties) {
		this.batchProperties = batchProperties;
	}

	@Bean(name = TMDB_TASK_EXECUTOR)
	public TaskExecutor tmdbTaskExecutor() {
		SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("tmdb-batch-");
		executor.setVirtualThreads(true);
		executor.setConcurrencyLimit(resolveConcurrencyLimit());
		executor.setRejectTasksWhenLimitReached(false);
		executor.setTaskTerminationTimeout(TASK_TERMINATION_TIMEOUT_MS);
		return executor;
	}

	private int resolveConcurrencyLimit() {
		if (batchProperties.tmdb() == null || batchProperties.tmdb().concurrencyLimit() == null) {
			return DEFAULT_CONCURRENCY_LIMIT;
		}

		int concurrencyLimit = batchProperties.tmdb().concurrencyLimit();
		if (concurrencyLimit < 1) {
			throw new IllegalStateException("flint.batch.tmdb.concurrency-limit must be greater than 0");
		}
		return concurrencyLimit;
	}
}
