package kr.flint.batch.config;

import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

// Job 실행을 비동기로 돌려 admin 트리거 endpoint가 즉시 응답할 수 있게 한다.
@Configuration
public class BatchJobLauncherConfig {

	@Bean(name = "asyncJobLauncher")
	public JobLauncher asyncJobLauncher(JobRepository jobRepository) throws Exception {
		TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
		launcher.setJobRepository(jobRepository);
		launcher.setTaskExecutor(new SimpleAsyncTaskExecutor("tmdb-job-launcher-"));
		launcher.afterPropertiesSet();
		return launcher;
	}
}
