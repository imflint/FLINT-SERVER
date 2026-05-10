package kr.flint.batch.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class TmdbBatchAsyncConfig {

	public static final String TMDB_TASK_EXECUTOR = "tmdbTaskExecutor";

	@Bean(name = TMDB_TASK_EXECUTOR)
	public TaskExecutor tmdbTaskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(50);
		executor.setMaxPoolSize(50);
		executor.setQueueCapacity(100);
		executor.setThreadNamePrefix("tmdb-batch-");
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.initialize();
		return executor;
	}
}
