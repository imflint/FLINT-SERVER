package kr.flint.batch.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import io.hypersistence.tsid.TSID;
import kr.flint.batch.sync.TmdbSyncRun;
import kr.flint.batch.sync.TmdbSyncRunStatus;
import kr.flint.batch.sync.TmdbSyncRunType;
import kr.flint.shared.exception.ErrorCode;
import kr.flint.shared.exception.GeneralException;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class TmdbSyncRunJdbcRepository {

    private static final String LOCK_NAME = "TMDB_CATALOG";

	private final JdbcTemplate jdbcTemplate;

	public boolean schemaReady() {
		Integer tableCount = jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM information_schema.tables
			WHERE table_schema = DATABASE()
			  AND table_name IN ('tmdb_sync_run', 'tmdb_sync_lock')
			""", Integer.class);
		return tableCount != null && tableCount == 2;
	}

    @Transactional
	public PreparedRun prepare(
        String runKey,
        TmdbSyncRunType runType,
        LocalDate businessDate,
        String ownerId,
		Duration leaseDuration
	) {
		jdbcTemplate.update("""
			INSERT IGNORE INTO tmdb_sync_lock (
				lock_name, owner_id, run_key, lease_until, heartbeat_at
			) VALUES (?, '', '', TIMESTAMP('1970-01-01 00:00:01'), TIMESTAMP('1970-01-01 00:00:01'))
			""", LOCK_NAME);
		LockState lock = jdbcTemplate.queryForObject("""
			SELECT run_key, lease_until >= UTC_TIMESTAMP() AS active
			FROM tmdb_sync_lock
			WHERE lock_name = ?
			FOR UPDATE
			""", (resultSet, rowNumber) -> new LockState(
			resultSet.getString("run_key"),
			resultSet.getBoolean("active")
		), LOCK_NAME);

		Optional<TmdbSyncRun> existing = findByRunKeyForUpdate(runKey);
		if (existing.isPresent() && !restartable(existing.get())) {
			return new PreparedRun(existing.get(), false);
		}
		if (lock != null && lock.active()) {
			throw new GeneralException(ErrorCode.CONFLICT, "다른 TMDB 동기화 작업이 실행 중입니다.");
		}

		if (existing.isEmpty()) {
			jdbcTemplate.update("""
			INSERT IGNORE INTO tmdb_sync_run (
                id, run_key, run_type, business_date, status,
                processed_count, total_count, created_at, updated_at
			) VALUES (?, ?, ?, ?, 'QUEUED', 0, 0, UTC_TIMESTAMP(), UTC_TIMESTAMP())
			""", TSID.Factory.getTsid().toLong(), runKey, runType.name(), businessDate);
		}

		int leaseSeconds = Math.toIntExact(leaseDuration.toSeconds());
		jdbcTemplate.update("""
			UPDATE tmdb_sync_lock
			SET owner_id = ?, run_key = ?,
				lease_until = DATE_ADD(UTC_TIMESTAMP(), INTERVAL ? SECOND),
				heartbeat_at = UTC_TIMESTAMP()
			WHERE lock_name = ?
			""", ownerId, runKey, leaseSeconds, LOCK_NAME);

        jdbcTemplate.update("""
            UPDATE tmdb_sync_run
            SET status = 'QUEUED', owner_id = ?,
                lease_until = DATE_ADD(UTC_TIMESTAMP(), INTERVAL ? SECOND),
                heartbeat_at = UTC_TIMESTAMP(), error_message = NULL, updated_at = UTC_TIMESTAMP()
            WHERE run_key = ?
            """, ownerId, leaseSeconds, runKey);
        return new PreparedRun(findByRunKey(runKey).orElseThrow(), true);
    }

	public Optional<TmdbSyncRun> findByRunKey(String runKey) {
        return jdbcTemplate.query(
            "SELECT * FROM tmdb_sync_run WHERE run_key = ?",
            (rs, rowNum) -> map(rs),
            runKey
        ).stream().findFirst();
    }

    public Optional<TmdbSyncRun> findById(Long id) {
        return jdbcTemplate.query(
            "SELECT * FROM tmdb_sync_run WHERE id = ?",
            (rs, rowNum) -> map(rs),
            id
        ).stream().findFirst();
    }

    public List<TmdbSyncRun> findRecent(int limit) {
        return jdbcTemplate.query(
            "SELECT * FROM tmdb_sync_run ORDER BY created_at DESC, id DESC LIMIT ?",
            (rs, rowNum) -> map(rs),
            limit
        );
    }

	public List<TmdbSyncRun> findRestartable() {
        return jdbcTemplate.query("""
            SELECT * FROM tmdb_sync_run
            WHERE status IN ('STOPPED', 'RUNNING', 'STOPPING')
              AND (lease_until IS NULL OR lease_until < UTC_TIMESTAMP())
            ORDER BY created_at ASC
            """, (rs, rowNum) -> map(rs));
    }

    public void markRunning(String runKey, Long jobExecutionId, long processedCount, long totalCount) {
        jdbcTemplate.update("""
            UPDATE tmdb_sync_run
            SET status = 'RUNNING', job_execution_id = ?, processed_count = ?, total_count = ?,
                heartbeat_at = UTC_TIMESTAMP(), updated_at = UTC_TIMESTAMP()
            WHERE run_key = ?
            """, jobExecutionId, processedCount, totalCount, runKey);
    }

	@Transactional
	public void markCompleted(String runKey, long processedCount, long totalCount) {
        jdbcTemplate.update("""
            UPDATE tmdb_sync_run
            SET status = 'COMPLETED', processed_count = ?, total_count = ?,
                lease_until = NULL, updated_at = UTC_TIMESTAMP()
            WHERE run_key = ?
            """, processedCount, totalCount, runKey);
        release(runKey);
    }

	@Transactional
	public void markFailed(String runKey, Throwable error) {
        String message = error == null ? null : String.valueOf(error.getMessage());
        if (message != null && message.length() > 1000) {
            message = message.substring(0, 1000);
        }
        jdbcTemplate.update("""
            UPDATE tmdb_sync_run
            SET status = 'FAILED', error_message = ?, lease_until = NULL, updated_at = UTC_TIMESTAMP()
            WHERE run_key = ?
            """, message, runKey);
        release(runKey);
    }

    public void markStoppingByOwner(String ownerId) {
        jdbcTemplate.update("""
            UPDATE tmdb_sync_run
            SET status = 'STOPPING', updated_at = UTC_TIMESTAMP()
            WHERE owner_id = ? AND status = 'RUNNING'
            """, ownerId);
    }

	@Transactional
	public void markStoppedByOwner(String ownerId) {
        jdbcTemplate.update("""
            UPDATE tmdb_sync_run
            SET status = 'STOPPED', lease_until = NULL, updated_at = UTC_TIMESTAMP()
            WHERE owner_id = ? AND status IN ('RUNNING', 'STOPPING')
            """, ownerId);
        jdbcTemplate.update("DELETE FROM tmdb_sync_lock WHERE lock_name = ? AND owner_id = ?", LOCK_NAME, ownerId);
    }

    public void heartbeat(String ownerId, Duration leaseDuration) {
        int leaseSeconds = Math.toIntExact(leaseDuration.toSeconds());
        jdbcTemplate.update("""
            UPDATE tmdb_sync_lock
            SET lease_until = DATE_ADD(UTC_TIMESTAMP(), INTERVAL ? SECOND), heartbeat_at = UTC_TIMESTAMP()
            WHERE lock_name = ? AND owner_id = ?
            """, leaseSeconds, LOCK_NAME, ownerId);
        jdbcTemplate.update("""
            UPDATE tmdb_sync_run
            SET lease_until = DATE_ADD(UTC_TIMESTAMP(), INTERVAL ? SECOND),
                heartbeat_at = UTC_TIMESTAMP(), updated_at = UTC_TIMESTAMP()
            WHERE owner_id = ? AND status IN ('QUEUED', 'RUNNING')
            """, leaseSeconds, ownerId);
    }

    private void release(String runKey) {
        jdbcTemplate.update("DELETE FROM tmdb_sync_lock WHERE lock_name = ? AND run_key = ?", LOCK_NAME, runKey);
    }

    private boolean restartable(TmdbSyncRun run) {
        if (run.status() == TmdbSyncRunStatus.STOPPED || run.status() == TmdbSyncRunStatus.FAILED) {
            return true;
        }
        return (run.status() == TmdbSyncRunStatus.RUNNING || run.status() == TmdbSyncRunStatus.STOPPING)
            && (run.leaseUntil() == null || run.leaseUntil().isBefore(LocalDateTime.now(java.time.Clock.systemUTC())));
    }

    private TmdbSyncRun map(ResultSet rs) throws SQLException {
        return new TmdbSyncRun(
            rs.getLong("id"),
            rs.getString("run_key"),
            TmdbSyncRunType.valueOf(rs.getString("run_type")),
            rs.getObject("business_date", LocalDate.class),
            TmdbSyncRunStatus.valueOf(rs.getString("status")),
            rs.getObject("job_execution_id", Long.class),
            rs.getString("owner_id"),
            toLocalDateTime(rs, "lease_until"),
            toLocalDateTime(rs, "heartbeat_at"),
            rs.getLong("processed_count"),
            rs.getLong("total_count"),
            rs.getString("error_message"),
            toLocalDateTime(rs, "created_at"),
            toLocalDateTime(rs, "updated_at")
        );
    }

    private LocalDateTime toLocalDateTime(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

	public record PreparedRun(TmdbSyncRun run, boolean launch) {
	}

	public Optional<LocalDate> findLatestCompletedDailyDateBefore(LocalDate businessDate) {
		return Optional.ofNullable(jdbcTemplate.queryForObject("""
			SELECT MAX(business_date)
			FROM tmdb_sync_run
			WHERE run_type = 'DAILY'
			  AND status = 'COMPLETED'
			  AND business_date < ?
			""", LocalDate.class, businessDate));
	}

	private Optional<TmdbSyncRun> findByRunKeyForUpdate(String runKey) {
		return jdbcTemplate.query(
			"SELECT * FROM tmdb_sync_run WHERE run_key = ? FOR UPDATE",
			(resultSet, rowNumber) -> map(resultSet),
			runKey
		).stream().findFirst();
	}

	private record LockState(String runKey, boolean active) {
	}
}
