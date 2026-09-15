package kr.flint.api.domain.exploration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;
import java.util.stream.IntStream;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import kr.flint.api.domain.exploration.dto.response.ExplorationSessionRes;
import kr.flint.api.domain.exploration.repository.ExplorationQueryRepository;
import kr.flint.api.domain.exploration.repository.ExplorationQueryRepository.ExploreContentRow;
import kr.flint.api.domain.exploration.repository.ExplorationQueryRepository.RepresentativeCollectionRow;
import kr.flint.api.domain.exploration.repository.ExplorationQueryRepository.ExposableSnapshotKey;
import kr.flint.exploration.domain.UserExplorationProgress;
import kr.flint.exploration.domain.UserExplorationSessionItem;
import kr.flint.exploration.exception.ExplorationException;
import kr.flint.exploration.service.ExplorationProgressService;
import kr.flint.infra.storage.cloudfront.CloudFrontUrlProvider;

@ExtendWith(MockitoExtension.class)
class ExplorationQueryFacadeTest {

	@Mock
	private ExplorationQueryRepository explorationQueryRepository;

	@Mock
	private ExplorationProgressService explorationProgressService;

	@Mock
	private CloudFrontUrlProvider cloudFrontUrlProvider;

	@InjectMocks
	private ExplorationQueryFacade explorationQueryFacade;

	@Test
	@DisplayName("탐색 작품 설명은 콘텐츠 줄거리 대신 대표 컬렉션의 선정 이유를 반환")
	void getSessionUsesRepresentativeCollectionReason() {
		Long userId = 1L;
		UserExplorationProgress progress = UserExplorationProgress.create(userId);
		List<ExploreContentRow> rows = LongStream.rangeClosed(1, 30)
			.mapToObj(id -> new ExploreContentRow(id, "작품 " + id, "poster.jpg", 2026))
			.toList();
		Map<Long, RepresentativeCollectionRow> representatives = new LinkedHashMap<>();
		rows.forEach(row -> representatives.put(
			row.contentId(),
			new RepresentativeCollectionRow(100L + row.contentId(), "사용자가 작성한 소개 " + row.contentId())
		));

		when(explorationProgressService.getOrCreate(userId)).thenReturn(progress);
		when(explorationQueryRepository.findSession(null, 30)).thenReturn(rows);
		when(explorationQueryRepository.findRepresentativeCollections(
			rows.stream().map(ExploreContentRow::contentId).toList()
		)).thenReturn(representatives);
		when(explorationQueryRepository.existsFullNextSession(30L, 30)).thenReturn(false);
		when(cloudFrontUrlProvider.resolveUrl("poster.jpg")).thenReturn("resolved/poster.jpg");

		ExplorationSessionRes response = explorationQueryFacade.getSession(userId);

		assertThat(response.items()).hasSize(30);
		assertThat(response.items().getFirst().description()).isEqualTo("사용자가 작성한 소개 1");
		assertThat(response.items().getFirst().collectionId()).isEqualTo(101L);
	}

	@Test
	@DisplayName("스냅샷 세션은 최초 한 번만 생성하고 재진입 시 같은 30개를 반환")
	void reusesCreatedSnapshotOnReentry() {
		enableSnapshot();
		Long userId = 1L;
		UserExplorationProgress progress = UserExplorationProgress.create(userId);
		List<ExploreContentRow> rows = rows();
		Map<Long, RepresentativeCollectionRow> representatives = representatives(rows);
		AtomicReference<List<UserExplorationSessionItem>> stored = new AtomicReference<>();
		when(explorationProgressService.getOrCreateForUpdate(userId)).thenReturn(progress);
		when(explorationProgressService.getSessionItems(progress))
			.thenAnswer(ignored -> stored.get() == null ? List.of() : stored.get());
		when(explorationQueryRepository.findSession(null, 30)).thenReturn(rows);
		when(explorationQueryRepository.findRepresentativeCollections(anyList())).thenReturn(representatives);
		when(explorationProgressService.saveSessionItems(anyList())).thenAnswer(invocation -> {
			List<UserExplorationSessionItem> items = invocation.getArgument(0);
			stored.set(items);
			return items;
		});
		when(explorationQueryRepository.findExposableSnapshotKeys(anyList(), anyList()))
			.thenReturn(exposableKeys(rows));
		when(explorationQueryRepository.existsFullNextSession(30L, 30)).thenReturn(false);
		when(cloudFrontUrlProvider.resolveUrl("poster.jpg")).thenReturn("resolved/poster.jpg");

		ExplorationSessionRes first = explorationQueryFacade.getSession(userId);
		ExplorationSessionRes second = explorationQueryFacade.getSession(userId);

		assertThat(first.items()).hasSize(30);
		assertThat(second.items()).extracting(item -> item.contentId()).containsExactlyElementsOf(
			first.items().stream().map(item -> item.contentId()).toList()
		);
		assertThat(second.items()).extracting(item -> item.position()).containsExactlyElementsOf(
			IntStream.rangeClosed(1, 30).boxed().toList()
		);
		verify(explorationQueryRepository, times(1)).findSession(null, 30);
	}

	@Test
	@DisplayName("세션 항목이 숨김 처리되면 제외하되 다른 작품으로 충원하지 않음")
	void hidesSnapshotItemWithoutRefill() {
		enableSnapshot();
		UserExplorationProgress progress = UserExplorationProgress.create(1L);
		List<UserExplorationSessionItem> items = snapshotItems(progress, rows(), representatives(rows()));
		Set<ExposableSnapshotKey> keys = exposableKeys(rows()).stream()
			.filter(key -> !key.contentId().equals(5L))
			.collect(java.util.stream.Collectors.toSet());
		when(explorationProgressService.getOrCreateForUpdate(1L)).thenReturn(progress);
		when(explorationProgressService.getSessionItems(progress)).thenReturn(items);
		when(explorationQueryRepository.findExposableSnapshotKeys(anyList(), anyList())).thenReturn(keys);
		when(explorationQueryRepository.existsFullNextSession(30L, 30)).thenReturn(false);
		when(cloudFrontUrlProvider.resolveUrl("poster.jpg")).thenReturn("resolved/poster.jpg");

		ExplorationSessionRes response = explorationQueryFacade.getSession(1L);

		assertThat(response.items()).hasSize(29);
		assertThat(response.items()).extracting(item -> item.contentId()).doesNotContain(5L);
		verify(explorationQueryRepository, never()).findSession(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
	}

	@Test
	@DisplayName("진행 위치를 건너뛰면 거부")
	void rejectsSkippedProgress() {
		enableSnapshot();
		UserExplorationProgress progress = UserExplorationProgress.create(1L);
		List<ExploreContentRow> rows = rows();
		List<UserExplorationSessionItem> items = snapshotItems(progress, rows, representatives(rows));
		when(explorationProgressService.getOrCreateForUpdate(1L)).thenReturn(progress);
		when(explorationProgressService.getSessionItems(progress)).thenReturn(items);
		when(explorationQueryRepository.findExposableSnapshotKeys(anyList(), anyList()))
			.thenReturn(exposableKeys(rows));

		assertThatThrownBy(() -> explorationQueryFacade.updateProgress(1L, 2))
			.isInstanceOf(ExplorationException.class);
	}

	@Test
	@DisplayName("마지막 위치 저장 시 세션을 완료 상태로 전환")
	void completesAtLastPosition() {
		enableSnapshot();
		UserExplorationProgress progress = UserExplorationProgress.create(1L);
		progress.recordViewedPosition(29);
		List<ExploreContentRow> rows = rows();
		List<UserExplorationSessionItem> items = snapshotItems(progress, rows, representatives(rows));
		when(explorationProgressService.getOrCreateForUpdate(1L)).thenReturn(progress);
		when(explorationProgressService.getSessionItems(progress)).thenReturn(items);
		when(explorationQueryRepository.findExposableSnapshotKeys(anyList(), anyList()))
			.thenReturn(exposableKeys(rows));
		when(explorationQueryRepository.existsFullNextSession(30L, 30)).thenReturn(false);
		when(cloudFrontUrlProvider.resolveUrl("poster.jpg")).thenReturn("resolved/poster.jpg");
		doAnswer(invocation -> {
			progress.recordViewedPosition(invocation.getArgument(1));
			return null;
		}).when(explorationProgressService).recordViewedPosition(progress, 30);
		doAnswer(invocation -> {
			progress.markCompleted();
			return null;
		}).when(explorationProgressService).markCompleted(progress);

		ExplorationSessionRes response = explorationQueryFacade.updateProgress(1L, 30);

		assertThat(response.lastViewedPosition()).isEqualTo(30);
		assertThat(response.canAdvance()).isTrue();
		verify(explorationProgressService).markCompleted(progress);
	}

	private void enableSnapshot() {
		ReflectionTestUtils.setField(explorationQueryFacade, "snapshotEnabled", true);
	}

	private List<ExploreContentRow> rows() {
		return LongStream.rangeClosed(1, 30)
			.mapToObj(id -> new ExploreContentRow(id, "작품 " + id, "poster.jpg", 2026))
			.toList();
	}

	private Map<Long, RepresentativeCollectionRow> representatives(List<ExploreContentRow> rows) {
		Map<Long, RepresentativeCollectionRow> result = new LinkedHashMap<>();
		rows.forEach(row -> result.put(
			row.contentId(),
			new RepresentativeCollectionRow(100L + row.contentId(), "소개 " + row.contentId())
		));
		return result;
	}

	private List<UserExplorationSessionItem> snapshotItems(
		UserExplorationProgress progress,
		List<ExploreContentRow> rows,
		Map<Long, RepresentativeCollectionRow> representatives
	) {
		return IntStream.range(0, rows.size())
			.mapToObj(index -> {
				ExploreContentRow row = rows.get(index);
				RepresentativeCollectionRow representative = representatives.get(row.contentId());
				return UserExplorationSessionItem.create(
					progress.getUserId(), progress.getSessionVersion(), index + 1,
					row.contentId(), row.title(), row.poster(), row.year(),
					representative.reason(), representative.collectionId()
				);
			})
			.toList();
	}

	private Set<ExposableSnapshotKey> exposableKeys(List<ExploreContentRow> rows) {
		return rows.stream()
			.map(row -> new ExposableSnapshotKey(row.contentId(), 100L + row.contentId()))
			.collect(java.util.stream.Collectors.toSet());
	}
}
