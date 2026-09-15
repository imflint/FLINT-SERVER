package kr.flint.api.admin.domain.batch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import kr.flint.api.admin.domain.batch.dto.request.LanguageCleanupExecuteReq;
import kr.flint.api.domain.home.service.CollectionKeywordSyncService;
import kr.flint.api.domain.home.service.RecommendationCacheService;
import kr.flint.batch.repository.TmdbCatalogCleanupJdbcRepository;
import kr.flint.batch.sync.TmdbPruneManifest;
import kr.flint.shared.exception.ErrorCode;
import kr.flint.shared.exception.GeneralException;

@ExtendWith(MockitoExtension.class)
class TmdbLanguageCleanupServiceTest {

	@Mock
	private TmdbCatalogCleanupJdbcRepository cleanupRepository;

	@Mock
	private CollectionKeywordSyncService collectionKeywordSyncService;

	@Mock
	private RecommendationCacheService recommendationCacheService;

	private TmdbLanguageCleanupService service;

	@BeforeEach
	void setUp() {
		service = new TmdbLanguageCleanupService(
			cleanupRepository,
			collectionKeywordSyncService,
			recommendationCacheService
		);
	}

	@Test
	void previewRejectsCatalogWithPendingOrRetryContents() {
		when(cleanupRepository.countUnclassifiedContents()).thenReturn(2L);

		assertThatThrownBy(service::preview)
			.isInstanceOfSatisfying(GeneralException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONFLICT)
			);
		verify(cleanupRepository, never()).findIneligibleContentIds();
	}

	@Test
	void executeRequiresSnapshotConfirmationBeforeDeleting() {
		var request = new LanguageCleanupExecuteReq(1L, "hash", false);

		assertThatThrownBy(() -> service.execute(request))
			.isInstanceOfSatisfying(GeneralException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONFLICT)
			);
		verify(cleanupRepository, never()).deleteNextChunk(1L, 500);
	}

	@Test
	void executeProcessesChunksThenRebuildsAffectedCollectionsAndCache() {
		TmdbPruneManifest preview = manifest("PREVIEW", 501, 0, null);
		TmdbPruneManifest completed = manifest("COMPLETED", 501, 501, LocalDateTime.now());
		when(cleanupRepository.findManifest(1L))
			.thenReturn(java.util.Optional.of(preview))
			.thenReturn(java.util.Optional.of(completed));
		when(cleanupRepository.deleteNextChunk(1L, 500)).thenReturn(500, 1, 0);
		when(cleanupRepository.findActiveAffectedCollectionIds(1L)).thenReturn(List.of(10L, 11L));

		var response = service.execute(new LanguageCleanupExecuteReq(1L, "hash", true));

		assertThat(response.status()).isEqualTo("COMPLETED");
		InOrder order = inOrder(cleanupRepository, collectionKeywordSyncService, recommendationCacheService);
		order.verify(cleanupRepository).promoteLocalizedTitlesForEligibleContents();
		order.verify(cleanupRepository).finalizeAffectedCollections(1L);
		order.verify(cleanupRepository).findActiveAffectedCollectionIds(1L);
		order.verify(collectionKeywordSyncService).fullSync(10L);
		order.verify(collectionKeywordSyncService).fullSync(11L);
		order.verify(recommendationCacheService).invalidateAllCache();
		order.verify(cleanupRepository).completeManifest(1L);
	}

	@Test
	void executeDoesNotCompleteManifestWhenCollectionRebuildFails() {
		TmdbPruneManifest executing = manifest("EXECUTING", 1, 1, null);
		when(cleanupRepository.findManifest(1L)).thenReturn(java.util.Optional.of(executing));
		when(cleanupRepository.deleteNextChunk(1L, 500)).thenReturn(0);
		when(cleanupRepository.findActiveAffectedCollectionIds(1L)).thenReturn(List.of(10L));
		doThrow(new IllegalStateException("sync failed")).when(collectionKeywordSyncService).fullSync(10L);

		assertThatThrownBy(() -> service.execute(new LanguageCleanupExecuteReq(1L, "hash", true)))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("sync failed");

		verify(cleanupRepository, never()).completeManifest(1L);
	}

	private TmdbPruneManifest manifest(
		String status,
		long candidateCount,
		long processedCount,
		LocalDateTime executedAt
	) {
		return new TmdbPruneManifest(
			1L,
			status,
			candidateCount,
			"hash",
			processedCount,
			LocalDateTime.of(2026, 9, 13, 0, 0),
			executedAt
		);
	}
}
