package kr.flint.api.admin.domain.batch.service;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import org.springframework.stereotype.Service;

import kr.flint.api.admin.domain.batch.dto.request.LanguageCleanupExecuteReq;
import kr.flint.api.admin.domain.batch.dto.response.LanguageCleanupManifestRes;
import kr.flint.api.domain.home.service.CollectionKeywordSyncService;
import kr.flint.api.domain.home.service.RecommendationCacheService;
import kr.flint.batch.repository.TmdbCatalogCleanupJdbcRepository;
import kr.flint.batch.sync.TmdbPruneManifest;
import kr.flint.shared.exception.ErrorCode;
import kr.flint.shared.exception.GeneralException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TmdbLanguageCleanupService {

    private static final int DELETE_CHUNK_SIZE = 500;

    private final TmdbCatalogCleanupJdbcRepository cleanupRepository;
    private final CollectionKeywordSyncService collectionKeywordSyncService;
    private final RecommendationCacheService recommendationCacheService;

    public LanguageCleanupManifestRes preview() {
        long unclassifiedCount = cleanupRepository.countUnclassifiedContents();
        if (unclassifiedCount > 0) {
            throw new GeneralException(
                ErrorCode.CONFLICT,
                "PENDING 또는 RETRY 상태를 포함한 미분류 콘텐츠가 남아 있습니다: " + unclassifiedCount
            );
        }

        List<Long> candidates = cleanupRepository.findIneligibleContentIds();
        String hash = hash(candidates);
        return LanguageCleanupManifestRes.from(cleanupRepository.createManifest(candidates, hash));
    }

    public LanguageCleanupManifestRes execute(LanguageCleanupExecuteReq request) {
        if (!request.snapshotConfirmed()) {
            throw new GeneralException(ErrorCode.CONFLICT, "RDS 수동 스냅샷 생성 확인이 필요합니다.");
        }

        TmdbPruneManifest manifest = cleanupRepository.findManifest(request.manifestId())
            .orElseThrow(() -> new GeneralException(ErrorCode.NOT_FOUND));
        if ("COMPLETED".equals(manifest.status()) && manifest.candidateHash().equals(request.candidateHash())) {
            return LanguageCleanupManifestRes.from(manifest);
        }
        if (!("PREVIEW".equals(manifest.status()) || "EXECUTING".equals(manifest.status()))
            || !manifest.candidateHash().equals(request.candidateHash())) {
            throw new GeneralException(ErrorCode.CONFLICT, "preview manifest 상태 또는 후보 해시가 일치하지 않습니다.");
        }

        while (cleanupRepository.deleteNextChunk(manifest.id(), DELETE_CHUNK_SIZE) > 0) {
            // 각 호출은 독립 트랜잭션으로 커밋되어 중단 후에도 이어서 실행할 수 있다.
        }
        cleanupRepository.promoteLocalizedTitlesForEligibleContents();
        cleanupRepository.finalizeAffectedCollections(manifest.id());

        for (Long collectionId : cleanupRepository.findActiveAffectedCollectionIds(manifest.id())) {
            collectionKeywordSyncService.fullSync(collectionId);
        }
        recommendationCacheService.invalidateAllCache();
        cleanupRepository.completeManifest(manifest.id());
        return LanguageCleanupManifestRes.from(cleanupRepository.findManifest(manifest.id()).orElseThrow());
    }

    private String hash(List<Long> contentIds) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
            for (Long contentId : contentIds) {
                buffer.clear();
                buffer.putLong(contentId);
                digest.update(buffer.array());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
