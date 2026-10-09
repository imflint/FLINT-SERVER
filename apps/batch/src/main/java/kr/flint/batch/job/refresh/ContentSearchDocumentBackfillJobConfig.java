package kr.flint.batch.job.refresh;

import java.util.Map;

import javax.sql.DataSource;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import kr.flint.batch.service.ContentSearchDocumentService;
import kr.flint.batch.service.ContentSearchDocumentService.DocumentSource;

@Configuration
@ConditionalOnProperty(name = "flint.content.search-document-backfill-enabled", havingValue = "true")
public class ContentSearchDocumentBackfillJobConfig {
    public static final String JOB_NAME = "contentSearchDocumentBackfillJob";

    @Bean
    public ApplicationRunner searchDocumentBackfillRunner(JobRepository repository,
        @Qualifier(JOB_NAME) Job job, ConfigurableApplicationContext context,
        @Value("${flint.content.search-document-backfill-version:v1}") String version) {
        return arguments -> {
            TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
            launcher.setJobRepository(repository);
            launcher.afterPropertiesSet();
            var execution = launcher.run(job, new JobParametersBuilder()
                .addString("backfillVersion",version).toJobParameters());
            if (execution.getStatus() != BatchStatus.COMPLETED) {
                throw new IllegalStateException("Search backfill failed: execution=" + execution.getId());
            }
            context.close();
        };
    }

    @Bean(name = JOB_NAME)
    public Job job(JobRepository repository, @Qualifier("contentSearchDocumentBackfillStep") Step step) {
        return new JobBuilder(JOB_NAME, repository).start(step).build();
    }

    @Bean
    public Step contentSearchDocumentBackfillStep(JobRepository repository, PlatformTransactionManager manager,
        @Qualifier("contentSearchDocumentReader") JdbcPagingItemReader<DocumentSource> reader,
        ContentSearchDocumentService service) {
        return new StepBuilder("contentSearchDocumentBackfillStep", repository)
            .<DocumentSource, DocumentSource>chunk(500, manager).reader(reader)
            .writer(chunk -> service.rewrite(chunk.getItems().stream().toList()))
            .listener(new StepExecutionListener() {
                @Override
                public void beforeStep(StepExecution execution) {
                    service.preflight();
                }
            }).build();
    }

    @Bean
    @StepScope
    public JdbcPagingItemReader<DocumentSource> contentSearchDocumentReader(DataSource dataSource) {
        return new JdbcPagingItemReaderBuilder<DocumentSource>().name("contentSearchDocumentReader")
            .dataSource(dataSource).selectClause("SELECT id, title_ko, title_en")
            .fromClause("FROM content").sortKeys(Map.of("id", Order.ASCENDING)).pageSize(500)
            .rowMapper((rs, row) -> new DocumentSource(rs.getLong("id"), rs.getString("title_ko"), rs.getString("title_en")))
            .saveState(true).build();
    }
}
