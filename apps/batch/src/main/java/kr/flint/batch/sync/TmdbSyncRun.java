package kr.flint.batch.sync;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record TmdbSyncRun(
    Long id,
    String runKey,
    TmdbSyncRunType runType,
    LocalDate businessDate,
    TmdbSyncRunStatus status,
    Long jobExecutionId,
    String ownerId,
    LocalDateTime leaseUntil,
    LocalDateTime heartbeatAt,
    long processedCount,
    long totalCount,
    String errorMessage,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public boolean terminal() {
        return status == TmdbSyncRunStatus.COMPLETED || status == TmdbSyncRunStatus.FAILED;
    }
}
