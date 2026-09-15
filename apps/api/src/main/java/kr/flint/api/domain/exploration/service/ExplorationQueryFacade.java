package kr.flint.api.domain.exploration.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import kr.flint.api.domain.exploration.dto.response.ExploreContentRes;
import kr.flint.api.domain.exploration.dto.response.ExplorationSessionRes;
import kr.flint.api.domain.exploration.dto.response.ExplorationState;
import kr.flint.api.domain.exploration.repository.ExplorationQueryRepository;
import kr.flint.api.domain.exploration.repository.ExplorationQueryRepository.ExposableSnapshotKey;
import kr.flint.api.domain.exploration.repository.ExplorationQueryRepository.ExploreContentRow;
import kr.flint.api.domain.exploration.repository.ExplorationQueryRepository.RepresentativeCollectionRow;
import kr.flint.exploration.domain.UserExplorationProgress;
import kr.flint.exploration.domain.UserExplorationSessionItem;
import kr.flint.exploration.exception.ExplorationErrorCode;
import kr.flint.exploration.exception.ExplorationException;
import kr.flint.exploration.service.ExplorationProgressService;
import kr.flint.infra.storage.cloudfront.CloudFrontUrlProvider;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@Transactional
public class ExplorationQueryFacade {

	private static final int SESSION_SIZE = 30;

	private final ExplorationQueryRepository explorationQueryRepository;
	private final ExplorationProgressService explorationProgressService;
	private final CloudFrontUrlProvider cloudFrontUrlProvider;

	@Value("${flint.exploration.snapshot-enabled:false}")
	private boolean snapshotEnabled;

	public ExplorationSessionRes getSession(Long userId) {
		if (!snapshotEnabled) {
			return buildLegacySession(explorationProgressService.getOrCreate(userId));
		}

		UserExplorationProgress progress = explorationProgressService.getOrCreateForUpdate(userId);
		List<UserExplorationSessionItem> items = getOrCreateSnapshot(progress);
		return buildSnapshotSession(progress, items);
	}

	public ExplorationSessionRes updateProgress(Long userId, int lastViewedPosition) {
		requireSnapshotEnabled();
		UserExplorationProgress progress = explorationProgressService.getOrCreateForUpdate(userId);
		List<UserExplorationSessionItem> items = getOrCreateSnapshot(progress);
		if (items.isEmpty()) {
			return ExplorationSessionRes.empty();
		}

		Set<Integer> exposablePositions = findExposablePositions(items);
		skipHiddenPositions(progress, exposablePositions);
		int currentPosition = progress.getLastViewedPosition();
		if (lastViewedPosition == currentPosition) {
			return buildSnapshotSession(progress, items, exposablePositions);
		}

		int expectedPosition = exposablePositions.stream()
			.filter(position -> position > currentPosition)
			.mapToInt(Integer::intValue)
			.min()
			.orElse(currentPosition);
		if (lastViewedPosition != expectedPosition) {
			throw new ExplorationException(ExplorationErrorCode.INVALID_PROGRESS);
		}

		explorationProgressService.recordViewedPosition(progress, lastViewedPosition);
		skipHiddenPositions(progress, exposablePositions);
		return buildSnapshotSession(progress, items, exposablePositions);
	}

	public ExplorationSessionRes advance(Long userId) {
		if (!snapshotEnabled) {
			return advanceLegacy(userId);
		}

		UserExplorationProgress progress = explorationProgressService.getOrCreateForUpdate(userId);
		List<UserExplorationSessionItem> currentItems = getOrCreateSnapshot(progress);
		if (currentItems.isEmpty()) {
			return ExplorationSessionRes.empty();
		}

		Set<Integer> exposablePositions = findExposablePositions(currentItems);
		skipHiddenPositions(progress, exposablePositions);
		if (!progress.isCompleted()) {
			throw new ExplorationException(ExplorationErrorCode.SESSION_NOT_COMPLETED);
		}

		Long lastContentId = currentItems.getLast().getContentId();
		if (!explorationQueryRepository.existsFullNextSession(lastContentId, SESSION_SIZE)) {
			return buildSnapshotSession(progress, currentItems, exposablePositions);
		}

		explorationProgressService.advanceTo(progress, lastContentId);
		return buildSnapshotSession(progress, getOrCreateSnapshot(progress));
	}

	private List<UserExplorationSessionItem> getOrCreateSnapshot(UserExplorationProgress progress) {
		List<UserExplorationSessionItem> existing = explorationProgressService.getSessionItems(progress);
		if (!existing.isEmpty()) {
			return existing;
		}

		List<ExploreContentRow> rows = explorationQueryRepository.findSession(progress.getSessionCursor(), SESSION_SIZE);
		if (rows.size() < SESSION_SIZE) {
			return List.of();
		}

		List<Long> contentIds = rows.stream().map(ExploreContentRow::contentId).toList();
		Map<Long, RepresentativeCollectionRow> representatives =
			explorationQueryRepository.findRepresentativeCollections(contentIds);
		if (representatives.size() != SESSION_SIZE) {
			return List.of();
		}

		List<UserExplorationSessionItem> snapshotItems = IntStream.range(0, rows.size())
			.mapToObj(index -> toSnapshotItem(progress, rows.get(index), representatives, index + 1))
			.toList();
		return explorationProgressService.saveSessionItems(snapshotItems);
	}

	private UserExplorationSessionItem toSnapshotItem(
		UserExplorationProgress progress,
		ExploreContentRow row,
		Map<Long, RepresentativeCollectionRow> representatives,
		int position
	) {
		RepresentativeCollectionRow representative = representatives.get(row.contentId());
		return UserExplorationSessionItem.create(
			progress.getUserId(),
			progress.getSessionVersion(),
			position,
			row.contentId(),
			row.title(),
			row.poster(),
			row.year(),
			representative.reason(),
			representative.collectionId()
		);
	}

	private ExplorationSessionRes buildSnapshotSession(
		UserExplorationProgress progress,
		List<UserExplorationSessionItem> items
	) {
		if (items.isEmpty()) {
			return ExplorationSessionRes.empty();
		}
		Set<Integer> exposablePositions = findExposablePositions(items);
		skipHiddenPositions(progress, exposablePositions);
		return buildSnapshotSession(progress, items, exposablePositions);
	}

	private ExplorationSessionRes buildSnapshotSession(
		UserExplorationProgress progress,
		List<UserExplorationSessionItem> items,
		Set<Integer> exposablePositions
	) {
		List<ExploreContentRes> responses = items.stream()
			.filter(item -> exposablePositions.contains(item.getPosition()))
			.map(this::toResponse)
			.toList();
		Long lastContentId = items.getLast().getContentId();
		boolean hasNext = explorationQueryRepository.existsFullNextSession(lastContentId, SESSION_SIZE);
		ExplorationState state = progress.isCompleted() ? ExplorationState.END : ExplorationState.IN_PROGRESS;
		return ExplorationSessionRes.of(
			responses,
			state,
			hasNext,
			progress.getLastViewedPosition(),
			progress.isCompleted()
		);
	}

	private Set<Integer> findExposablePositions(List<UserExplorationSessionItem> items) {
		Set<ExposableSnapshotKey> exposableKeys = explorationQueryRepository.findExposableSnapshotKeys(
			items.stream().map(UserExplorationSessionItem::getContentId).toList(),
			items.stream().map(UserExplorationSessionItem::getCollectionId).toList()
		);
		return items.stream()
			.filter(item -> exposableKeys.contains(new ExposableSnapshotKey(
				item.getContentId(),
				item.getCollectionId()
			)))
			.map(UserExplorationSessionItem::getPosition)
			.collect(java.util.stream.Collectors.toSet());
	}

	private void skipHiddenPositions(UserExplorationProgress progress, Set<Integer> exposablePositions) {
		int position = progress.getLastViewedPosition();
		while (position < SESSION_SIZE && !exposablePositions.contains(position + 1)) {
			position++;
		}
		if (position != progress.getLastViewedPosition()) {
			explorationProgressService.recordViewedPosition(progress, position);
		}
		if (position == SESSION_SIZE && !progress.isCompleted()) {
			explorationProgressService.markCompleted(progress);
		}
	}

	private ExploreContentRes toResponse(UserExplorationSessionItem item) {
		return new ExploreContentRes(
			item.getContentId(),
			item.getTitle(),
			item.getDescription(),
			StringUtils.hasText(item.getPoster()) ? cloudFrontUrlProvider.resolveUrl(item.getPoster()) : null,
			item.getYear(),
			item.getCollectionId(),
			item.getPosition()
		);
	}

	private ExplorationSessionRes advanceLegacy(Long userId) {
		UserExplorationProgress progress = explorationProgressService.getOrCreate(userId);
		List<ExploreContentRow> rows = explorationQueryRepository.findSession(progress.getSessionCursor(), SESSION_SIZE);
		if (rows.size() < SESSION_SIZE) {
			return buildLegacySession(progress);
		}

		Long lastContentId = rows.getLast().contentId();
		if (explorationQueryRepository.existsFullNextSession(lastContentId, SESSION_SIZE)) {
			explorationProgressService.advanceTo(progress, lastContentId);
		} else {
			explorationProgressService.markCompleted(progress);
		}
		return buildLegacySession(progress);
	}

	private ExplorationSessionRes buildLegacySession(UserExplorationProgress progress) {
		List<ExploreContentRow> rows = explorationQueryRepository.findSession(progress.getSessionCursor(), SESSION_SIZE);
		if (rows.size() < SESSION_SIZE) {
			return ExplorationSessionRes.empty();
		}

		List<Long> contentIds = rows.stream().map(ExploreContentRow::contentId).toList();
		Map<Long, RepresentativeCollectionRow> representativeMap =
			explorationQueryRepository.findRepresentativeCollections(contentIds);
		List<ExploreContentRes> items = IntStream.range(0, rows.size())
			.mapToObj(index -> {
				ExploreContentRow row = rows.get(index);
				RepresentativeCollectionRow representative = representativeMap.get(row.contentId());
				return new ExploreContentRes(
					row.contentId(),
					row.title(),
					representative.reason(),
					StringUtils.hasText(row.poster()) ? cloudFrontUrlProvider.resolveUrl(row.poster()) : null,
					row.year(),
					representative.collectionId(),
					index + 1
				);
			})
			.toList();

		Long lastContentId = rows.getLast().contentId();
		boolean hasNext = explorationQueryRepository.existsFullNextSession(lastContentId, SESSION_SIZE);
		ExplorationState state = progress.isCompleted() ? ExplorationState.END : ExplorationState.IN_PROGRESS;
		return ExplorationSessionRes.of(
			items,
			state,
			hasNext,
			progress.isCompleted() ? SESSION_SIZE : 0,
			progress.isCompleted()
		);
	}

	private void requireSnapshotEnabled() {
		if (!snapshotEnabled) {
			throw new ExplorationException(ExplorationErrorCode.SNAPSHOT_DISABLED);
		}
	}
}
