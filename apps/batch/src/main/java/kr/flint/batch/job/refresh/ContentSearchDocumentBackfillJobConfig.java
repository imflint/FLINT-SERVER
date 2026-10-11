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

    @Value("${flint.content.search-document-backfill-words-only:false}")
    private boolean wordsOnly;

    @Value("${flint.content.search-document-backfill-characters-only:false}")
    private boolean charactersOnly;

    @Bean
    public ApplicationRunner searchDocumentBackfillRunner(JobRepository repository,
        @Qualifier(JOB_NAME) Job job, ConfigurableApplicationContext context,
        @Value("${flint.content.search-document-backfill-version:v1}") String version) {
        return arguments -> {
            TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
            launcher.setJobRepository(repository);
            launcher.afterPropertiesSet();
            var parameters = new JobParametersBuilder().addString("backfillVersion",version);
            if (charactersOnly) parameters.addString("documentMode", "CHARACTERS");
            var execution = launcher.run(job, parameters.toJobParameters());
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
        if (wordsOnly && charactersOnly) {
            throw new IllegalStateException("Word-only and character-only backfill cannot be selected together");
        }
        return new StepBuilder("contentSearchDocumentBackfillStep", repository)
            .<DocumentSource, DocumentSource>chunk(500, manager).reader(reader)
            .writer(chunk -> {
                if (charactersOnly) service.rewriteCharacters(chunk.getItems().stream().toList());
                else if (wordsOnly) service.rewriteWords(chunk.getItems().stream().toList());
                else service.rewrite(chunk.getItems().stream().toList());
            })
            .listener(new StepExecutionListener() {
                @Override
                public void beforeStep(StepExecution execution) {
                    if (charactersOnly) service.preflightCharacters();
                    else service.preflight();
                }

                @Override
                public org.springframework.batch.core.ExitStatus afterStep(StepExecution execution) {
                    if (charactersOnly && execution.getStatus() == BatchStatus.COMPLETED) {
                        try {
                            service.verifyCharacterOrphans();
                        } catch (RuntimeException failure) {
                            execution.setStatus(BatchStatus.FAILED);
                            execution.addFailureException(failure);
                            return org.springframework.batch.core.ExitStatus.FAILED.addExitDescription(failure);
                        }
                    }
                    return null;
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
