package kr.flint.api.admin.domain.batch.service;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import kr.flint.api.admin.domain.batch.dto.request.LanguageCleanupExecuteReq;
import kr.flint.api.admin.domain.batch.dto.response.LanguageCleanupManifestRes;
import kr.flint.api.domain.home.service.CollectionKeywordSyncService;
import kr.flint.api.domain.home.service.RecommendationCacheService;
import kr.flint.batch.repository.TmdbCatalogCleanupJdbcRepository;
import kr.flint.batch.repository.TmdbSyncRunJdbcRepository;
import kr.flint.batch.service.TmdbApiException;
import kr.flint.batch.sync.TmdbPruneManifest;
import kr.flint.batch.service.TmdbContentSyncService;
import kr.flint.shared.exception.ErrorCode;
import kr.flint.shared.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TmdbLanguageCleanupService {

    private static final int DELETE_CHUNK_SIZE = 500;
    private static final Duration LEASE_DURATION = Duration.ofMinutes(2);

    private final TmdbCatalogCleanupJdbcRepository cleanupRepository;
    private final CollectionKeywordSyncService collectionKeywordSyncService;
    private final RecommendationCacheService recommendationCacheService;
    private final TmdbContentSyncService contentSyncService;
    private final TmdbSyncRunJdbcRepository runRepository;
    private final ConcurrentHashMap<String, CleanupLease> activeLeases = new ConcurrentHashMap<>();

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

        contentSyncService.ensureSchemaReady();
        String ownerId = UUID.randomUUID().toString();
        runRepository.acquireCleanupLease(manifest.id(), ownerId, LEASE_DURATION);
        CleanupLease lease = new CleanupLease(manifest.id(), ownerId, new AtomicBoolean(false));
        activeLeases.put(ownerId, lease);
        try {
            cleanupRepository.validateRemainingCandidates(manifest.id(), ownerId);
            while (deleteNextChunk(lease) > 0) {
                // 청크별 트랜잭션과 체크포인트로 중단 후 재개한다.
            }
            long lastId = 0;
            while (true) {
                assertLease(lease);
                var progress = contentSyncService.promoteTitleChunk(lastId, 50);
                if (progress.processedCount() == 0) break;
                lastId = progress.lastId();
            }
            assertLease(lease);
            repairAffectedCollections(manifest.id());
            cleanupRepository.completeManifest(manifest.id());
            return LanguageCleanupManifestRes.from(cleanupRepository.findManifest(manifest.id()).orElseThrow());
        } catch (RuntimeException failure) {
            if (!lease.lost().get() && cleanupRepository.findManifest(manifest.id())
                .map(current -> current.processedCount() > 0).orElse(false)) {
                try {
                    assertLease(lease);
                    repairAffectedCollections(manifest.id());
                } catch (RuntimeException repairFailure) {
                    log.warn("TMDB cleanup repair deferred manifestId={} cause={}", manifest.id(), TmdbApiException.safeMessage(repairFailure));
                }
            }
            throw failure;
        } finally {
            activeLeases.remove(ownerId);
            runRepository.releaseCleanupLease(manifest.id(), ownerId);
        }
    }

    @Scheduled(fixedDelay = 30_000)
    public void heartbeatCleanup() {
        for (CleanupLease lease : activeLeases.values()) {
            try {
                if (!runRepository.renewCleanupLease(lease.manifestId(), lease.ownerId(), LEASE_DURATION)) {
                    lease.lost().set(true);
                }
            } catch (RuntimeException failure) {
                lease.lost().set(true);
                log.warn("TMDB cleanup lease renewal failed manifestId={} cause={}", lease.manifestId(), TmdbApiException.safeMessage(failure));
            }
        }
    }

    private int deleteNextChunk(CleanupLease lease) {
        assertLease(lease);
        return cleanupRepository.deleteNextChunk(lease.manifestId(), lease.ownerId(), DELETE_CHUNK_SIZE);
    }

    private void assertLease(CleanupLease lease) {
        if (lease.lost().get() || !runRepository.renewCleanupLease(lease.manifestId(), lease.ownerId(), LEASE_DURATION)) {
            lease.lost().set(true);
            throw new GeneralException(ErrorCode.CONFLICT, "TMDB 정리 작업의 임대가 만료되었습니다.");
        }
    }

    private void repairAffectedCollections(Long manifestId) {
        cleanupRepository.finalizeAffectedCollections(manifestId);
        for (Long collectionId : cleanupRepository.findActiveAffectedCollectionIds(manifestId)) {
            collectionKeywordSyncService.fullSync(collectionId);
        }
        recommendationCacheService.invalidateAllCache();
    }

    private record CleanupLease(Long manifestId, String ownerId, AtomicBoolean lost) {}

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
