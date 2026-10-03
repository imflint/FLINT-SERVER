package kr.flint.api.domain.bookmark.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.persistence.EntityManager;
import kr.flint.api.domain.auth.service.AuthFacade;
import kr.flint.api.domain.bookmark.dto.response.GetBookmarkUserRes;
import kr.flint.api.global.oauth.client.AppleOAuthClient;
import kr.flint.api.global.oauth.client.KakaoOAuthClient;
import kr.flint.auth.service.AuthService;
import kr.flint.auth.service.UserIdentityService;
import kr.flint.bookmark.domain.CollectionBookmark;
import kr.flint.bookmark.service.BookmarkCommandService;
import kr.flint.bookmark.service.BookmarkQueryService;
import kr.flint.collection.domain.Collection;
import kr.flint.collection.service.CollectionService;
import kr.flint.content.service.ContentService;
import kr.flint.exploration.service.ExplorationProgressService;
import kr.flint.infra.storage.cloudfront.CloudFrontUrlProvider;
import kr.flint.infra.storage.cloudfront.properties.CloudFrontProperties;
import kr.flint.ott.service.OttService;
import kr.flint.shared.config.QueryDslConfig;
import kr.flint.taste.service.TasteService;
import kr.flint.terms.domain.TermsContext;
import kr.flint.terms.service.TermsService;
import kr.flint.user.domain.User;
import kr.flint.user.exception.UserException;
import kr.flint.user.service.UserService;

@DataJpaTest(showSql = false)
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = CollectionBookmarkIntegrityServiceTest.Config.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CollectionBookmarkIntegrityServiceTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("flint_test")
        .withUsername("flint")
        .withPassword("flint");

    @Autowired private BookmarkCommandFacade commandFacade;
    @Autowired private BookmarkQueryFacade queryFacade;
    @Autowired private AuthFacade authFacade;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoSpyBean private CollectionService collectionService;
    @MockitoSpyBean private BookmarkQueryService bookmarkQueryService;
    @MockitoSpyBean private BookmarkCommandService bookmarkCommandService;
    @MockitoBean private AuthService authService;
    @MockitoBean private UserIdentityService userIdentityService;
    @MockitoBean private AppleOAuthClient appleOAuthClient;
    @MockitoBean private KakaoOAuthClient kakaoOAuthClient;
    @MockitoBean private ContentService contentService;
    @MockitoBean private OttService ottService;
    @MockitoBean private TasteService tasteService;
    @MockitoBean private TermsService termsService;
    @MockitoBean private ExplorationProgressService explorationProgressService;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "8");
    }

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("DELETE FROM collection_bookmark");
        jdbc.update("DELETE FROM content_bookmark");
        jdbc.update("DELETE FROM collection");
        jdbc.update("DELETE FROM `user`");
        if (jdbc.queryForObject("""
            SELECT COUNT(*) FROM information_schema.table_constraints
            WHERE constraint_schema = DATABASE() AND table_name = 'collection_bookmark'
                AND constraint_name = 'fk_collection_bookmark_user'
            """, Integer.class) == 0) {
            jdbc.execute("""
                ALTER TABLE collection_bookmark ADD CONSTRAINT fk_collection_bookmark_user
                FOREIGN KEY (user_id) REFERENCES `user` (id) ON DELETE RESTRICT ON UPDATE RESTRICT
                """);
        }
    }

    @Test
    @DisplayName("저장과 취소 후 DB 카운트와 전체 저장 사용자 목록이 일치")
    void bookmarkAndCancel() {
        Fixture fixture = fixture();
        assertThat(queryFacade.getBookmarkedUser(fixture.collectionId()).userList()).isEmpty();
        assertThat(commandFacade.toggleCollection(fixture.firstUser(), fixture.collectionId())).isTrue();
        assertCounts(fixture.collectionId(), 1);
        GetBookmarkUserRes saved = queryFacade.getBookmarkedUser(fixture.collectionId());
        assertThat(saved.bookmarkCount()).isEqualTo(1);
        assertThat(saved.userList()).hasSize(1);
        assertThat(commandFacade.toggleCollection(fixture.firstUser(), fixture.collectionId())).isFalse();
        assertCounts(fixture.collectionId(), 0);
        assertThat(queryFacade.getBookmarkedUser(fixture.collectionId()).bookmarkCount()).isZero();
    }

    @Test
    @DisplayName("외래 키가 있어도 북마크를 먼저 정리하여 탈퇴에 성공")
    void withdrawalRemovesBookmarksBeforeUser() {
        Fixture fixture = fixture();
        commandFacade.toggleCollection(fixture.firstUser(), fixture.collectionId());
        commandFacade.toggleCollection(fixture.secondUser(), fixture.collectionId());
        authFacade.withdraw(fixture.firstUser(), "test-access", List.of());
        assertCounts(fixture.collectionId(), 1);
        assertThat(userCount(fixture.firstUser())).isZero();
        assertThat(queryFacade.getBookmarkedUser(fixture.collectionId()).userList()).hasSize(1);
    }

    @Test
    @DisplayName("soft-delete 컬렉션의 카운트도 탈퇴 후 실제 관계 수로 동기화")
    void withdrawalSynchronizesSoftDeletedCollection() {
        Fixture fixture = fixture();
        commandFacade.toggleCollection(fixture.firstUser(), fixture.collectionId());
        transaction().executeWithoutResult(status -> entityManager.find(Collection.class, fixture.collectionId())
            .delete(LocalDateTime.now()));
        authFacade.withdraw(fixture.firstUser(), "test-access", List.of());
        assertCounts(fixture.collectionId(), 0);
    }

    @Test
    @DisplayName("탈퇴 도중 실패하면 북마크와 카운트 및 사용자가 모두 복구")
    void withdrawalRollsBackDatabaseChanges() {
        Fixture fixture = fixture();
        commandFacade.toggleCollection(fixture.firstUser(), fixture.collectionId());
        doThrow(new IllegalStateException("taste cleanup failed"))
            .when(tasteService).deleteUserKeywords(fixture.firstUser());
        assertThatThrownBy(() -> authFacade.withdraw(fixture.firstUser(), "test-access", List.of()))
            .isInstanceOf(IllegalStateException.class);
        assertCounts(fixture.collectionId(), 1);
        assertThat(userCount(fixture.firstUser())).isEqualTo(1);
    }

    @Test
    @DisplayName("사용자 외래 키는 없는 사용자의 삽입과 북마크가 남은 사용자 삭제를 거부")
    void foreignKeyRejectsInvalidRelationships() {
        Fixture fixture = fixture();
        assertThatThrownBy(() -> jdbc.update(
            "INSERT INTO collection_bookmark (id, user_id, collection_id) VALUES (1, -1, ?)",
            fixture.collectionId())).isInstanceOf(DataIntegrityViolationException.class);
        commandFacade.toggleCollection(fixture.firstUser(), fixture.collectionId());
        assertThatThrownBy(() -> jdbc.update("DELETE FROM `user` WHERE id = ?", fixture.firstUser()))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertCounts(fixture.collectionId(), 1);
    }

    @Test
    @DisplayName("서로 다른 사용자의 동시 저장은 두 관계와 카운트 2를 유지")
    void concurrentBookmarks() throws Exception {
        Fixture fixture = fixture();
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> {
                await(start);
                return commandFacade.toggleCollection(fixture.firstUser(), fixture.collectionId());
            });
            Future<Boolean> second = executor.submit(() -> {
                await(start);
                return commandFacade.toggleCollection(fixture.secondUser(), fixture.collectionId());
            });
            start.countDown();
            assertThat(first.get(15, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(15, TimeUnit.SECONDS)).isTrue();
        }
        assertCounts(fixture.collectionId(), 2);
    }

    @Test
    @DisplayName("잠금 전에 다른 저장이 완료되어도 오래된 스냅샷의 카운트를 사용하지 않음")
    void recountUsesFreshSnapshot() throws Exception {
        Fixture fixture = fixture();
        CountDownLatch snapshotRead = new CountDownLatch(1);
        CountDownLatch otherCommitted = new CountDownLatch(1);
        AtomicReference<Thread> delayedThread = new AtomicReference<>();
        doAnswer(invocation -> {
            if (Thread.currentThread() == delayedThread.get()) {
                jdbc.queryForObject("SELECT COUNT(*) FROM collection_bookmark", Integer.class);
                snapshotRead.countDown();
                await(otherCommitted);
            }
            return invocation.callRealMethod();
        }).when(collectionService).getActiveCollectionByIdForUpdate(fixture.collectionId());
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<Boolean> delayed = executor.submit(() -> {
                delayedThread.set(Thread.currentThread());
                return commandFacade.toggleCollection(fixture.firstUser(), fixture.collectionId());
            });
            try {
                await(snapshotRead);
                assertThat(commandFacade.toggleCollection(fixture.secondUser(), fixture.collectionId())).isTrue();
            } finally {
                otherCommitted.countDown();
            }
            assertThat(delayed.get(15, TimeUnit.SECONDS)).isTrue();
        }
        assertCounts(fixture.collectionId(), 2);
    }

    @Test
    @DisplayName("같은 사용자의 탈퇴가 진행 중이면 저장은 대기하고 삭제된 사용자를 거부")
    void bookmarkWaitsForWithdrawalOfSameUser() throws Exception {
        Fixture fixture = fixture();
        CountDownLatch withdrawing = new CountDownLatch(1);
        CountDownLatch finishWithdrawal = new CountDownLatch(1);
        doAnswer(invocation -> {
            withdrawing.countDown();
            await(finishWithdrawal);
            return null;
        }).when(termsService).validateAndCreateAgreements(eq(fixture.firstUser()), eq(TermsContext.WITHDRAWAL), anyList());
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> withdrawal = executor.submit(() -> authFacade.withdraw(fixture.firstUser(), "test-access", List.of()));
            await(withdrawing);
            Future<Boolean> bookmark = executor.submit(() -> commandFacade.toggleCollection(fixture.firstUser(), fixture.collectionId()));
            try {
                assertThatThrownBy(() -> bookmark.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            } finally {
                finishWithdrawal.countDown();
            }
            withdrawal.get(15, TimeUnit.SECONDS);
            assertThatThrownBy(() -> bookmark.get(15, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(UserException.class);
        }
        assertCounts(fixture.collectionId(), 0);
        assertNoOrphans();
    }

    @Test
    @DisplayName("다른 사용자의 저장도 탈퇴가 잠근 컬렉션에서는 대기한 후 정확히 집계")
    void otherUserBookmarkWaitsForWithdrawalCollectionLock() throws Exception {
        Fixture fixture = fixture();
        commandFacade.toggleCollection(fixture.firstUser(), fixture.collectionId());
        CountDownLatch deletingBookmarks = new CountDownLatch(1);
        CountDownLatch allowDelete = new CountDownLatch(1);
        doAnswer(invocation -> {
            deletingBookmarks.countDown();
            await(allowDelete);
            return invocation.callRealMethod();
        }).when(bookmarkCommandService).deleteBookmarkByUser(fixture.firstUser());
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> withdrawal = executor.submit(() -> authFacade.withdraw(fixture.firstUser(), "test-access", List.of()));
            try {
                await(deletingBookmarks);
                Future<Boolean> bookmark = executor.submit(() -> commandFacade.toggleCollection(fixture.secondUser(), fixture.collectionId()));
                assertThatThrownBy(() -> bookmark.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                allowDelete.countDown();
                withdrawal.get(15, TimeUnit.SECONDS);
                assertThat(bookmark.get(15, TimeUnit.SECONDS)).isTrue();
            } finally {
                allowDelete.countDown();
            }
        }
        assertCounts(fixture.collectionId(), 1);
        assertNoOrphans();
    }

    @Test
    @DisplayName("사용자 목록을 읽는 도중 탈퇴가 완료되어도 카운트와 목록은 같은 스냅샷")
    void bookmarkUsersUseOneSnapshot() throws Exception {
        Fixture fixture = fixture();
        commandFacade.toggleCollection(fixture.firstUser(), fixture.collectionId());
        commandFacade.toggleCollection(fixture.secondUser(), fixture.collectionId());
        CountDownLatch countRead = new CountDownLatch(1);
        CountDownLatch withdrawn = new CountDownLatch(1);
        AtomicReference<Thread> reader = new AtomicReference<>();
        doAnswer(invocation -> {
            Object count = invocation.callRealMethod();
            if (Thread.currentThread() == reader.get()) {
                countRead.countDown();
                await(withdrawn);
            }
            return count;
        }).when(bookmarkQueryService).getBookmarkCount(anyLong());
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<GetBookmarkUserRes> response = executor.submit(() -> {
                reader.set(Thread.currentThread());
                return queryFacade.getBookmarkedUser(fixture.collectionId());
            });
            try {
                await(countRead);
                authFacade.withdraw(fixture.firstUser(), "test-access", List.of());
            } finally {
                withdrawn.countDown();
            }
            GetBookmarkUserRes result = response.get(15, TimeUnit.SECONDS);
            assertThat(result.bookmarkCount()).isEqualTo(2);
            assertThat(result.userList()).hasSize(2);
        }
        assertCounts(fixture.collectionId(), 1);
    }

    private Fixture fixture() {
        return transaction().execute(status -> {
            User owner = User.createFling("owner");
            User first = User.createFling("first");
            User second = User.createFling("second");
            entityManager.persist(owner);
            entityManager.persist(first);
            entityManager.persist(second);
            Collection collection = Collection.create("collection", "description", null, true, owner.getId());
            entityManager.persist(collection);
            entityManager.flush();
            return new Fixture(first.getId(), second.getId(), collection.getId());
        });
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private void assertCounts(Long collectionId, int expected) {
        assertThat(jdbc.queryForObject("SELECT bookmark_count FROM collection WHERE id = ?", Integer.class, collectionId))
            .isEqualTo(expected);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM collection_bookmark WHERE collection_id = ?", Integer.class, collectionId))
            .isEqualTo(expected);
    }

    private int userCount(Long userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM `user` WHERE id = ?", Integer.class, userId);
    }

    private void assertNoOrphans() {
        assertThat(jdbc.queryForObject("""
            SELECT COUNT(*) FROM collection_bookmark cb LEFT JOIN `user` u ON u.id = cb.user_id WHERE u.id IS NULL
            """, Integer.class)).isZero();
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        if (!latch.await(15, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting for concurrent transaction");
        }
    }

    private record Fixture(Long firstUser, Long secondUser, Long collectionId) { }

    @TestConfiguration(proxyBeanMethods = false)
    @EntityScan(basePackages = "kr.flint")
    @EnableJpaAuditing
    @Import({BookmarkCommandFacade.class, BookmarkQueryFacade.class, AuthFacade.class, UserService.class,
        BookmarkCommandService.class, BookmarkQueryService.class, CollectionService.class, QueryDslConfig.class})
    static class Config {
        @Bean
        CloudFrontUrlProvider cloudFrontUrlProvider() {
            return new CloudFrontUrlProvider(new CloudFrontProperties("https://cdn.flint.kr", true));
        }
    }
}
