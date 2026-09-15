package kr.flint.api.admin.domain.batch.dto.response;

import java.time.LocalDateTime;

import kr.flint.batch.sync.TmdbPruneManifest;

public record LanguageCleanupManifestRes(
    Long manifestId,
    String status,
    long candidateCount,
    String candidateHash,
    long processedCount,
    LocalDateTime createdAt,
    LocalDateTime executedAt
) {
    public static LanguageCleanupManifestRes from(TmdbPruneManifest manifest) {
        return new LanguageCleanupManifestRes(
            manifest.id(), manifest.status(), manifest.candidateCount(), manifest.candidateHash(),
            manifest.processedCount(), manifest.createdAt(), manifest.executedAt()
        );
    }
}
