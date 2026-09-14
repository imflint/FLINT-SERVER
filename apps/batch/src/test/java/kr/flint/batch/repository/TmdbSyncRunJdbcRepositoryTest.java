package kr.flint.batch.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import kr.flint.batch.sync.TmdbSyncRunStatus;
import kr.flint.batch.sync.TmdbSyncRunType;
import kr.flint.shared.exception.GeneralException;

@Testcontainers(disabledWithoutDocker = true)
class TmdbSyncRunJdbcRepositoryTest {

	@Container
	private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
		.withDatabaseName("flint")
		.withUsername("test")
		.withPassword("test");

	private JdbcTemplate jdbcTemplate;
	private TmdbSyncRunJdbcRepository repository;
	private TransactionTemplate transactionTemplate;

	@BeforeEach
	void setUp() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setDriverClassName(MYSQL.getDriverClassName());
		dataSource.setUrl(MYSQL.getJdbcUrl());
		dataSource.setUsername(MYSQL.getUsername());
		dataSource.setPassword(MYSQL.getPassword());
		jdbcTemplate = new JdbcTemplate(dataSource);
		repository = new TmdbSyncRunJdbcRepository(jdbcTemplate);
		transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
		recreateSchema();
	}

	@Test
	void leasePreventsDuplicateAndConcurrentWorkflows() {
		LocalDate date = LocalDate.of(2026, 9, 13);
		var first = repository.prepare(
			"DAILY:2026-09-13", TmdbSyncRunType.DAILY, date, "blue", Duration.ofMinutes(2)
		);
		var duplicate = repository.prepare(
			"DAILY:2026-09-13", TmdbSyncRunType.DAILY, date, "green", Duration.ofMinutes(2)
		);

		assertThat(first.launch()).isTrue();
		assertThat(duplicate.launch()).isFalse();
		assertThat(duplicate.run().id()).isEqualTo(first.run().id());
		assertThatThrownBy(() -> repository.prepare(
			"MONTHLY:2026-09", TmdbSyncRunType.MONTHLY, date, "green", Duration.ofMinutes(2)
		)).isInstanceOf(GeneralException.class);

		repository.markCompleted("DAILY:2026-09-13", 5, 5);
		var monthly = repository.prepare(
			"MONTHLY:2026-09", TmdbSyncRunType.MONTHLY, date, "green", Duration.ofMinutes(2)
		);

		assertThat(monthly.launch()).isTrue();
		assertThat(first.run().status()).isEqualTo(TmdbSyncRunStatus.QUEUED);
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tmdb_sync_run", Integer.class))
			.isEqualTo(2);
	}

	@Test
	void atomicLeaseAllowsOnlyOneOfTwoConcurrentBusinessKeys() throws Exception {
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var daily = executor.submit(() -> attemptPrepare(
				"DAILY:2026-09-13",
				TmdbSyncRunType.DAILY,
				"blue",
				ready,
				start
			));
			var monthly = executor.submit(() -> attemptPrepare(
				"MONTHLY:2026-09",
				TmdbSyncRunType.MONTHLY,
				"green",
				ready,
				start
			));

			ready.await();
			start.countDown();

			assertThat(List.of(daily.get(), monthly.get()))
				.extracting(LeaseAttempt::result)
				.containsExactlyInAnyOrder("LAUNCHED", "CONFLICT");
		}
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tmdb_sync_run", Integer.class))
			.isEqualTo(1);
	}

	private LeaseAttempt attemptPrepare(
		String runKey,
		TmdbSyncRunType runType,
		String owner,
		CountDownLatch ready,
		CountDownLatch start
	) throws InterruptedException {
		ready.countDown();
		start.await();
		try {
			Boolean launched = transactionTemplate.execute(status -> repository.prepare(
				runKey,
				runType,
				LocalDate.of(2026, 9, 13),
				owner,
				Duration.ofMinutes(2)
			).launch());
			return new LeaseAttempt(Boolean.TRUE.equals(launched) ? "LAUNCHED" : "EXISTING");
		} catch (GeneralException exception) {
			return new LeaseAttempt("CONFLICT");
		}
	}

	private void recreateSchema() {
		jdbcTemplate.execute("DROP TABLE IF EXISTS tmdb_sync_lock");
		jdbcTemplate.execute("DROP TABLE IF EXISTS tmdb_sync_run");
		jdbcTemplate.execute("""
			CREATE TABLE tmdb_sync_run (
				id BIGINT NOT NULL PRIMARY KEY,
				run_key VARCHAR(64) NOT NULL UNIQUE,
				run_type VARCHAR(16) NOT NULL,
				business_date DATE NOT NULL,
				status VARCHAR(16) NOT NULL,
				job_execution_id BIGINT,
				owner_id VARCHAR(64),
				lease_until DATETIME(6),
				heartbeat_at DATETIME(6),
				processed_count BIGINT NOT NULL,
				total_count BIGINT NOT NULL,
				error_message VARCHAR(1000),
				created_at DATETIME(6) NOT NULL,
				updated_at DATETIME(6) NOT NULL
			)
			""");
		jdbcTemplate.execute("""
			CREATE TABLE tmdb_sync_lock (
				lock_name VARCHAR(64) NOT NULL PRIMARY KEY,
				owner_id VARCHAR(64) NOT NULL,
				run_key VARCHAR(64) NOT NULL,
				lease_until DATETIME(6) NOT NULL,
				heartbeat_at DATETIME(6) NOT NULL
			)
			""");
	}

	private record LeaseAttempt(String result) {
	}
}
