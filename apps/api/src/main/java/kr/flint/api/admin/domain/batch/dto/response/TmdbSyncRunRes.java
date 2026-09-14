package kr.flint.api.admin.domain.batch.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;

import kr.flint.batch.sync.TmdbSyncRun;

public record TmdbSyncRunRes(
    Long id,
    String runKey,
    String runType,
    LocalDate businessDate,
    String status,
    Long jobExecutionId,
    long processedCount,
    long totalCount,
    String errorMessage,
    LocalDateTime heartbeatAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static TmdbSyncRunRes from(TmdbSyncRun run) {
        return new TmdbSyncRunRes(
            run.id(), run.runKey(), run.runType().name(), run.businessDate(), run.status().name(),
            run.jobExecutionId(), run.processedCount(), run.totalCount(), run.errorMessage(),
            run.heartbeatAt(), run.createdAt(), run.updatedAt()
        );
    }
}
