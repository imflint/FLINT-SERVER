package kr.flint.batch.config;

import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.core.task.TaskExecutor;

// Job 실행을 비동기로 돌려 admin 트리거 endpoint가 즉시 응답할 수 있게 한다.
@Configuration
public class BatchJobLauncherConfig {

	@Bean(name = "asyncJobLauncher")
	public JobLauncher asyncJobLauncher(JobRepository jobRepository) throws Exception {
		TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
		launcher.setJobRepository(jobRepository);
		launcher.setTaskExecutor(batchJobExecutor());
		launcher.afterPropertiesSet();
		return launcher;
	}

	@Bean(name = "batchJobExecutor")
	public TaskExecutor batchJobExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setThreadNamePrefix("tmdb-job-launcher-");
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(1);
		executor.setQueueCapacity(8);
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(300);
		executor.initialize();
		return executor;
	}

	@Bean(name = "catalogWorkflowExecutor")
	public TaskExecutor catalogWorkflowExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setThreadNamePrefix("tmdb-workflow-");
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(1);
		executor.setQueueCapacity(1);
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(300);
		executor.initialize();
		return executor;
	}
}
