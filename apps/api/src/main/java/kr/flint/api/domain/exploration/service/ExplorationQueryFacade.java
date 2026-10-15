package kr.flint.api.domain.exploration.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

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

	public ExplorationSessionRes getSession(Long userId) {
		UserExplorationProgress progress = explorationProgressService.getOrCreateForUpdate(userId);
		List<UserExplorationSessionItem> items = getOrCreateSnapshot(progress);
		if (items.isEmpty()) {
			return ExplorationSessionRes.empty();
		}

		Set<Integer> exposablePositions = findExposablePositions(items);
		skipHiddenPositions(progress, exposablePositions);
		if (progress.isCompleted()) {
			Long lastContentId = items.getLast().getContentId();
			// Prepare a complete next snapshot before changing the locked progress row.
			List<UserExplorationSessionItem> nextItems = prepareSnapshot(
				progress.getUserId(), progress.getSessionVersion() + 1, lastContentId
			);
			if (!nextItems.isEmpty()) {
				explorationProgressService.advanceTo(progress, lastContentId);
				items = explorationProgressService.saveSessionItems(nextItems);
				exposablePositions = findExposablePositions(items);
				skipHiddenPositions(progress, exposablePositions);
			}
		}
		return buildSnapshotSession(progress, items, exposablePositions);
	}

	public ExplorationSessionRes updateProgress(Long userId, int lastViewedPosition) {
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

	private List<UserExplorationSessionItem> getOrCreateSnapshot(UserExplorationProgress progress) {
		List<UserExplorationSessionItem> existing = explorationProgressService.getSessionItems(progress);
		if (!existing.isEmpty()) {
			return existing;
		}

		List<UserExplorationSessionItem> items = prepareSnapshot(
			progress.getUserId(), progress.getSessionVersion(), progress.getSessionCursor()
		);
		return items.isEmpty() ? List.of() : explorationProgressService.saveSessionItems(items);
	}

	private List<UserExplorationSessionItem> prepareSnapshot(Long userId, int sessionVersion, Long cursor) {
		List<ExploreContentRow> rows = explorationQueryRepository.findSession(cursor, SESSION_SIZE);
		if (rows.size() < SESSION_SIZE) {
			return List.of();
		}

		List<Long> contentIds = rows.stream().map(ExploreContentRow::contentId).toList();
		Map<Long, RepresentativeCollectionRow> representatives =
			explorationQueryRepository.findRepresentativeCollections(contentIds);
		if (representatives.size() != SESSION_SIZE) {
			return List.of();
		}

		return IntStream.range(0, rows.size())
			.mapToObj(index -> toSnapshotItem(userId, sessionVersion, rows.get(index), representatives, index + 1))
			.toList();
	}

	private UserExplorationSessionItem toSnapshotItem(
		Long userId,
		int sessionVersion,
		ExploreContentRow row,
		Map<Long, RepresentativeCollectionRow> representatives,
		int position
	) {
		RepresentativeCollectionRow representative = representatives.get(row.contentId());
		return UserExplorationSessionItem.create(
			userId,
			sessionVersion,
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
			progress.getLastViewedPosition()
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
}
