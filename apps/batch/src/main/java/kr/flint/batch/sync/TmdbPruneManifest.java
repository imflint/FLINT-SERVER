package kr.flint.batch.sync;

import java.time.LocalDateTime;

public record TmdbPruneManifest(
    Long id,
    String status,
    long candidateCount,
    String candidateHash,
    long processedCount,
    LocalDateTime createdAt,
    LocalDateTime executedAt
) {
}
