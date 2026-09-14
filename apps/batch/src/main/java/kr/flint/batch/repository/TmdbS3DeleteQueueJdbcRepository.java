package kr.flint.batch.repository;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class TmdbS3DeleteQueueJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    public List<DeleteTarget> findDue(int limit) {
        return jdbcTemplate.query("""
            SELECT id, object_key, retry_count
            FROM tmdb_s3_delete_queue
            WHERE status = 'PENDING'
              AND delete_after <= UTC_TIMESTAMP()
              AND (next_retry_at IS NULL OR next_retry_at <= UTC_TIMESTAMP())
            ORDER BY delete_after, id
            LIMIT ?
            """, (rs, rowNum) -> new DeleteTarget(
            rs.getLong("id"), rs.getString("object_key"), rs.getInt("retry_count")
        ), limit);
    }

    public void markDeleted(Long id) {
        jdbcTemplate.update("""
            UPDATE tmdb_s3_delete_queue
            SET status = 'DELETED', deleted_at = UTC_TIMESTAMP(), updated_at = UTC_TIMESTAMP()
            WHERE id = ?
            """, id);
    }

    public void markFailed(Long id, int currentRetryCount, String errorMessage) {
        int nextRetryCount = currentRetryCount + 1;
        String nextStatus = nextRetryCount >= 10 ? "FAILED" : "PENDING";
        String safeMessage = errorMessage == null ? null : errorMessage.substring(0, Math.min(1000, errorMessage.length()));
        jdbcTemplate.update("""
            UPDATE tmdb_s3_delete_queue
            SET status = ?, retry_count = ?, error_message = ?,
                next_retry_at = DATE_ADD(UTC_TIMESTAMP(), INTERVAL 1 DAY), updated_at = UTC_TIMESTAMP()
            WHERE id = ?
            """, nextStatus, nextRetryCount, safeMessage, id);
    }

    public record DeleteTarget(Long id, String objectKey, int retryCount) {
    }
}
