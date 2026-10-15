package kr.flint.api.domain.exploration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import kr.flint.api.domain.exploration.dto.response.ExplorationSessionRes;
import kr.flint.api.domain.exploration.dto.response.ExplorationState;
import kr.flint.api.domain.exploration.repository.ExplorationQueryRepository;
import kr.flint.collection.domain.Collection;
import kr.flint.collection.domain.CollectionContent;
import kr.flint.content.domain.Content;
import kr.flint.content.domain.MediaType;
import kr.flint.exploration.domain.UserExplorationProgress;
import kr.flint.exploration.domain.UserExplorationSessionItem;
import kr.flint.exploration.repository.UserExplorationProgressRepository;
import kr.flint.exploration.repository.UserExplorationSessionItemRepository;
import kr.flint.exploration.service.ExplorationProgressService;
import kr.flint.infra.storage.cloudfront.CloudFrontUrlProvider;
import kr.flint.infra.storage.cloudfront.properties.CloudFrontProperties;
import kr.flint.shared.config.QueryDslConfig;

@DataJpaTest(showSql = false)
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = ExplorationSessionIntegrityServiceTest.Config.class)
@ActiveProfiles("exploration-integrity")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ExplorationSessionIntegrityServiceTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("exploration_test")
        .withPassword(UUID.randomUUID().toString());

    @Autowired private ExplorationQueryFacade facade;
    @Autowired private FailingProgressService progressService;
    @Autowired private UserExplorationProgressRepository progressRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MySQLDialect");
    }

    @BeforeEach
    void cleanDatabase() {
        progressService.failNextSave(false);
        for (String table : List.of("user_exploration_session_item", "user_exploration_progress",
            "collection_content_images", "collection_content", "content", "collection")) {
            jdbc.update("DELETE FROM " + table);
        }
    }

    @Test
    void concurrentGetsCreateExactlyOneNextSnapshot() throws Exception {
        List<Long> ids = seedCompletedSession(60);
        List<ExplorationSessionRes> responses = concurrentGets();
        ExplorationSessionRes firstResponse = responses.getFirst();

        assertThat(firstResponse).isEqualTo(responses.getLast());
        assertThat(firstResponse.state()).isEqualTo(ExplorationState.IN_PROGRESS);
        assertThat(firstResponse.lastViewedPosition()).isZero();
        assertThat(firstResponse.items()).extracting(item -> item.contentId())
            .containsExactlyElementsOf(ids.subList(30, 60));
        assertProgress(2, ids.get(29), 0, false);
        assertThat(snapshotCount(1)).isEqualTo(30);
        assertThat(snapshotCount(2)).isEqualTo(30);
        assertThat(facade.getSession(1L)).isEqualTo(firstResponse);
    }

    @Test
    void concurrentFirstGetsCreateExactlyOneInitialSession() throws Exception {
        List<Long> ids = seedCompletedSession(30);
        jdbc.update("DELETE FROM user_exploration_session_item");
        jdbc.update("DELETE FROM user_exploration_progress");

        List<ExplorationSessionRes> responses = concurrentGets();

        assertThat(responses.getFirst()).isEqualTo(responses.getLast());
        assertThat(responses.getFirst().items()).extracting(item -> item.contentId())
            .containsExactlyElementsOf(ids);
        assertProgress(1, null, 0, false);
        assertThat(snapshotCount(1)).isEqualTo(30);
    }

    private List<ExplorationSessionRes> concurrentGets() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var task = (java.util.concurrent.Callable<ExplorationSessionRes>) () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Concurrent GET start timed out");
                }
                return facade.getSession(1L);
            };
            var first = executor.submit(task);
            var second = executor.submit(task);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            return List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void incompleteNextSessionDoesNotAdvancePersistedProgress() {
        seedCompletedSession(59);

        ExplorationSessionRes response = facade.getSession(1L);

        assertThat(response.state()).isEqualTo(ExplorationState.END);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.items()).hasSize(30);
        assertProgress(1, null, 30, true);
        assertThat(snapshotCount(2)).isZero();
    }

    @Test
    void failedSnapshotWriteRollsBackProgressAndInsertedItems() {
        seedCompletedSession(60);
        progressService.failNextSave(true);

        assertThatThrownBy(() -> facade.getSession(1L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Injected snapshot write failure");

        assertProgress(1, null, 30, true);
        assertThat(snapshotCount(1)).isEqualTo(30);
        assertThat(snapshotCount(2)).isZero();
    }

    private List<Long> seedCompletedSession(int count) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Collection collection = Collection.create("collection", "description", null, true, 1L);
            entityManager.persist(collection);
            List<Content> contents = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                Content content = Content.create(index + 1L, MediaType.MOVIE,
                    "content " + index, 2026, "author", "description", "poster.jpg");
                entityManager.persist(content);
                contents.add(content);
            }
            entityManager.flush();
            contents.sort(Comparator.comparing(Content::getId));
            for (int index = 0; index < count; index++) {
                entityManager.persist(CollectionContent.create(
                    collection, contents.get(index).getId(), false, "reason " + index, index));
            }
            UserExplorationProgress progress = UserExplorationProgress.create(1L);
            progress.recordViewedPosition(30);
            progress.markCompleted();
            entityManager.persist(progress);
            IntStream.range(0, 30).forEach(index -> {
                Content content = contents.get(index);
                entityManager.persist(UserExplorationSessionItem.create(1L, 1, index + 1,
                    content.getId(), content.getTitle(), content.getPoster(), content.getYear(),
                    "reason " + index, collection.getId()));
            });
            entityManager.flush();
            return contents.stream().map(Content::getId).toList();
        });
    }

    private void assertProgress(int version, Long cursor, int position, boolean completed) {
        UserExplorationProgress stored = progressRepository.findByUserId(1L).orElseThrow();
        assertThat(stored.getSessionVersion()).isEqualTo(version);
        assertThat(stored.getSessionCursor()).isEqualTo(cursor);
        assertThat(stored.getLastViewedPosition()).isEqualTo(position);
        assertThat(stored.isCompleted()).isEqualTo(completed);
    }

    private int snapshotCount(int version) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM user_exploration_session_item "
            + "WHERE user_id = 1 AND session_version = ?", Integer.class, version);
    }

    @Configuration(proxyBeanMethods = false)
    @Profile("exploration-integrity")
    @EntityScan(basePackageClasses = {Collection.class, Content.class, UserExplorationProgress.class})
    @EnableJpaRepositories(basePackageClasses = UserExplorationProgressRepository.class)
    @EnableJpaAuditing
    @Import({ExplorationQueryFacade.class, ExplorationQueryRepository.class, QueryDslConfig.class})
    static class Config {
        @Bean
        FailingProgressService progressService(UserExplorationProgressRepository progress,
            UserExplorationSessionItemRepository items) {
            return new FailingProgressService(progress, items);
        }

        @Bean
        CloudFrontUrlProvider cloudFrontUrlProvider() {
            return new CloudFrontUrlProvider(new CloudFrontProperties("https://example.com", true));
        }
    }

    static class FailingProgressService extends ExplorationProgressService {
        private final UserExplorationSessionItemRepository items;
        private boolean failNextSave;

        FailingProgressService(UserExplorationProgressRepository progress,
            UserExplorationSessionItemRepository items) {
            super(progress, items);
            this.items = items;
        }

        public void failNextSave(boolean fail) {
            this.failNextSave = fail;
        }

        @Override
        public List<UserExplorationSessionItem> saveSessionItems(List<UserExplorationSessionItem> snapshot) {
            List<UserExplorationSessionItem> saved = super.saveSessionItems(snapshot);
            if (failNextSave) {
                items.flush();
                throw new IllegalStateException("Injected snapshot write failure");
            }
            return saved;
        }
    }
}
