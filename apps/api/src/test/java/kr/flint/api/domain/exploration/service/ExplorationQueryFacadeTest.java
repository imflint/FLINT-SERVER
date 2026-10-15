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

import kr.flint.api.domain.exploration.dto.response.ExplorationSessionRes;
import kr.flint.api.domain.exploration.dto.response.ExplorationState;
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

		when(explorationProgressService.getOrCreateForUpdate(userId)).thenReturn(progress);
		when(explorationQueryRepository.findSession(null, 30)).thenReturn(rows);
		when(explorationQueryRepository.findRepresentativeCollections(
			rows.stream().map(ExploreContentRow::contentId).toList()
		)).thenReturn(representatives);
		when(explorationProgressService.saveSessionItems(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
		when(explorationQueryRepository.findExposableSnapshotKeys(anyList(), anyList()))
			.thenReturn(exposableKeys(rows));
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
		assertThat(response.state()).isEqualTo(ExplorationState.END);
		assertThat(response.hasNext()).isFalse();
		assertThat(progress.getSessionVersion()).isEqualTo(1);
		assertThat(progress.getSessionCursor()).isNull();
		verify(explorationProgressService).markCompleted(progress);
		verify(explorationQueryRepository, never()).findSession(30L, 30);
	}

	@Test
	@DisplayName("현재 세션의 같은 위치 재전송은 허용하고 위치 회귀는 거부")
	void preservesProgressOnDuplicateAndRejectsRegression() {
		UserExplorationProgress progress = UserExplorationProgress.create(1L);
		progress.recordViewedPosition(10);
		stubStoredSnapshots(progress);

		ExplorationSessionRes response = explorationQueryFacade.updateProgress(1L, 10);

		assertThat(response.lastViewedPosition()).isEqualTo(10);
		assertThat(response.items()).hasSize(30);
		assertThatThrownBy(() -> explorationQueryFacade.updateProgress(1L, 9))
			.isInstanceOf(ExplorationException.class);
		assertThat(progress.getLastViewedPosition()).isEqualTo(10);
		assertThat(progress.getSessionVersion()).isEqualTo(1);
	}

	@Test
	@DisplayName("최초 후보가 30개 미만이면 진행 상태를 바꾸지 않고 EMPTY 반환")
	void returnsEmptyForIncompleteInitialSession() {
		UserExplorationProgress progress = UserExplorationProgress.create(1L);
		when(explorationProgressService.getOrCreateForUpdate(1L)).thenReturn(progress);
		when(explorationQueryRepository.findSession(null, 30)).thenReturn(rows().subList(0, 29));

		ExplorationSessionRes response = explorationQueryFacade.getSession(1L);

		assertThat(response.state()).isEqualTo(ExplorationState.EMPTY);
		assertThat(response.items()).isEmpty();
		assertThat(progress.getSessionCursor()).isNull();
		assertThat(progress.getSessionVersion()).isEqualTo(1);
		verify(explorationProgressService, never()).saveSessionItems(anyList());
	}

	@Test
	@DisplayName("완료 후 GET은 다음 세션으로 한 번만 이동하고 재조회는 같은 스냅샷 반환")
	void advancesCompletedSessionAndReusesNextSnapshot() {
		UserExplorationProgress progress = completedProgress();
		Map<Integer, List<UserExplorationSessionItem>> stored = stubStoredSnapshots(progress);
		stubNextSession(rows(31));
		stubAdvance(progress);

		ExplorationSessionRes first = explorationQueryFacade.getSession(1L);
		ExplorationSessionRes second = explorationQueryFacade.getSession(1L);

		assertThat(first.state()).isEqualTo(ExplorationState.IN_PROGRESS);
		assertThat(first.lastViewedPosition()).isZero();
		assertThat(first.items()).extracting(item -> item.contentId())
			.containsExactlyElementsOf(LongStream.rangeClosed(31, 60).boxed().toList());
		assertThat(second).isEqualTo(first);
		assertThat(progress.getSessionCursor()).isEqualTo(30L);
		assertThat(progress.getSessionVersion()).isEqualTo(2);
		assertThat(stored).hasSize(2);
		assertThat(stored.get(2)).hasSize(30);
		verify(explorationProgressService, times(1)).saveSessionItems(anyList());
	}

	@Test
	@DisplayName("다음 30개가 없으면 완료 상태와 커서 및 버전을 유지")
	void retainsCompletedSessionWhenNextSessionIsIncomplete() {
		UserExplorationProgress progress = completedProgress();
		stubStoredSnapshots(progress);
		when(explorationQueryRepository.findSession(30L, 30)).thenReturn(rows(31).subList(0, 29));

		ExplorationSessionRes response = explorationQueryFacade.getSession(1L);

		assertThat(response.state()).isEqualTo(ExplorationState.END);
		assertThat(response.hasNext()).isFalse();
		assertThat(response.lastViewedPosition()).isEqualTo(30);
		assertThat(response.items()).hasSize(30);
		assertThat(progress.getSessionCursor()).isNull();
		assertThat(progress.getSessionVersion()).isEqualTo(1);
		verify(explorationProgressService, never()).saveSessionItems(anyList());
	}

	@Test
	@DisplayName("다음 작품이 나중에 30개가 되면 같은 완료 세션의 GET에서 전환")
	void advancesWhenNextSessionBecomesAvailable() {
		UserExplorationProgress progress = completedProgress();
		stubStoredSnapshots(progress);
		List<ExploreContentRow> nextRows = rows(31);
		when(explorationQueryRepository.findSession(30L, 30))
			.thenReturn(nextRows.subList(0, 29)).thenReturn(nextRows);
		when(explorationQueryRepository.findRepresentativeCollections(anyList()))
			.thenReturn(representatives(nextRows));
		stubAdvance(progress);

		assertThat(explorationQueryFacade.getSession(1L).state()).isEqualTo(ExplorationState.END);
		assertThat(progress.getSessionVersion()).isEqualTo(1);
		assertThat(explorationQueryFacade.getSession(1L).state()).isEqualTo(ExplorationState.IN_PROGRESS);
		assertThat(progress.getSessionVersion()).isEqualTo(2);
	}

	@Test
	@DisplayName("대표 정보를 모두 구성하지 못하면 완료 세션을 이동시키지 않음")
	void retainsProgressWhenRepresentativesAreIncomplete() {
		UserExplorationProgress progress = completedProgress();
		stubStoredSnapshots(progress);
		List<ExploreContentRow> nextRows = rows(31);
		Map<Long, RepresentativeCollectionRow> representatives = representatives(nextRows);
		representatives.remove(60L);
		when(explorationQueryRepository.findSession(30L, 30)).thenReturn(nextRows);
		when(explorationQueryRepository.findRepresentativeCollections(anyList())).thenReturn(representatives);

		ExplorationSessionRes response = explorationQueryFacade.getSession(1L);

		assertThat(response.state()).isEqualTo(ExplorationState.END);
		assertThat(progress.getSessionCursor()).isNull();
		assertThat(progress.getSessionVersion()).isEqualTo(1);
		assertThat(progress.getLastViewedPosition()).isEqualTo(30);
		verify(explorationProgressService, never()).saveSessionItems(anyList());
	}

	@Test
	@DisplayName("마지막 스냅샷 작품이 숨겨져도 응답의 마지막 ID가 아닌 스냅샷 경계로 전환")
	void advancesFromSnapshotBoundaryWhenLastItemIsHidden() {
		UserExplorationProgress progress = completedProgress();
		stubStoredSnapshots(progress);
		stubNextSession(rows(31));
		when(explorationQueryRepository.findExposableSnapshotKeys(anyList(), anyList()))
			.thenReturn(exposableKeys(rows()).stream().filter(key -> key.contentId() != 30L)
				.collect(java.util.stream.Collectors.toSet())).thenReturn(exposableKeys(rows(31)));
		stubAdvance(progress);

		ExplorationSessionRes response = explorationQueryFacade.getSession(1L);

		assertThat(response.items().getFirst().contentId()).isEqualTo(31L);
		assertThat(progress.getSessionCursor()).isEqualTo(30L);
		assertThat(progress.getSessionVersion()).isEqualTo(2);
	}

	@Test
	@DisplayName("남은 작품이 모두 숨겨지면 GET에서 완료 후 자동 전환")
	void completesHiddenRemainderAndAdvances() {
		UserExplorationProgress progress = UserExplorationProgress.create(1L);
		progress.recordViewedPosition(29);
		stubStoredSnapshots(progress);
		stubNextSession(rows(31));
		when(explorationQueryRepository.findExposableSnapshotKeys(anyList(), anyList()))
			.thenReturn(exposableKeys(rows()).stream().filter(key -> key.contentId() != 30L)
				.collect(java.util.stream.Collectors.toSet())).thenReturn(exposableKeys(rows(31)));
		stubHiddenCompletion(progress);
		stubAdvance(progress);

		ExplorationSessionRes response = explorationQueryFacade.getSession(1L);

		assertThat(response.state()).isEqualTo(ExplorationState.IN_PROGRESS);
		assertThat(response.lastViewedPosition()).isZero();
		assertThat(progress.getSessionVersion()).isEqualTo(2);
	}

	@Test
	@DisplayName("새 세션이 모두 숨겨져도 한 GET에서 두 번 전환하지 않음")
	void advancesAtMostOncePerGet() {
		UserExplorationProgress progress = completedProgress();
		stubStoredSnapshots(progress);
		stubNextSession(rows(31));
		when(explorationQueryRepository.findExposableSnapshotKeys(anyList(), anyList())).thenReturn(Set.of());
		when(explorationQueryRepository.existsFullNextSession(60L, 30)).thenReturn(true);
		stubHiddenCompletion(progress);
		stubAdvance(progress);

		ExplorationSessionRes response = explorationQueryFacade.getSession(1L);

		assertThat(response.state()).isEqualTo(ExplorationState.END);
		assertThat(response.items()).isEmpty();
		assertThat(response.hasNext()).isTrue();
		assertThat(progress.getSessionVersion()).isEqualTo(2);
		verify(explorationQueryRepository, never()).findSession(60L, 30);
	}

	private UserExplorationProgress completedProgress() {
		UserExplorationProgress progress = UserExplorationProgress.create(1L);
		progress.recordViewedPosition(30);
		progress.markCompleted();
		return progress;
	}

	private Map<Integer, List<UserExplorationSessionItem>> stubStoredSnapshots(UserExplorationProgress progress) {
		Map<Integer, List<UserExplorationSessionItem>> stored = new LinkedHashMap<>();
		stored.put(1, snapshotItems(progress, rows(), representatives(rows())));
		when(explorationProgressService.getOrCreateForUpdate(1L)).thenReturn(progress);
		when(explorationProgressService.getSessionItems(progress))
			.thenAnswer(ignored -> stored.getOrDefault(progress.getSessionVersion(), List.of()));
		org.mockito.Mockito.lenient().when(explorationProgressService.saveSessionItems(anyList())).thenAnswer(invocation -> {
			List<UserExplorationSessionItem> items = invocation.getArgument(0);
			stored.put(items.getFirst().getSessionVersion(), items);
			return items;
		});
		when(explorationQueryRepository.findExposableSnapshotKeys(anyList(), anyList())).thenAnswer(invocation -> {
			List<Long> ids = invocation.getArgument(0);
			return ids.stream().map(id -> new ExposableSnapshotKey(id, 100L + id))
				.collect(java.util.stream.Collectors.toSet());
		});
		org.mockito.Mockito.lenient().when(cloudFrontUrlProvider.resolveUrl("poster.jpg")).thenReturn("resolved/poster.jpg");
		return stored;
	}

	private void stubNextSession(List<ExploreContentRow> nextRows) {
		when(explorationQueryRepository.findSession(30L, 30)).thenReturn(nextRows);
		when(explorationQueryRepository.findRepresentativeCollections(anyList())).thenReturn(representatives(nextRows));
	}

	private void stubAdvance(UserExplorationProgress progress) {
		doAnswer(invocation -> {
			progress.advance(invocation.getArgument(1));
			return null;
		}).when(explorationProgressService).advanceTo(progress, 30L);
	}

	private void stubHiddenCompletion(UserExplorationProgress progress) {
		doAnswer(invocation -> {
			progress.recordViewedPosition(invocation.getArgument(1));
			return null;
		}).when(explorationProgressService).recordViewedPosition(progress, 30);
		doAnswer(invocation -> {
			progress.markCompleted();
			return null;
		}).when(explorationProgressService).markCompleted(progress);
	}

	private List<ExploreContentRow> rows() {
		return rows(1);
	}

	private List<ExploreContentRow> rows(long firstId) {
		return LongStream.range(firstId, firstId + 30)
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
