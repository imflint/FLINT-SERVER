package kr.flint.api.domain.exploration.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import kr.flint.api.domain.exploration.dto.response.ExploreContentRes;
import kr.flint.api.domain.exploration.dto.response.ExplorationSessionRes;
import kr.flint.api.domain.exploration.dto.response.ExplorationState;
import kr.flint.api.domain.exploration.repository.ExplorationQueryRepository;
import kr.flint.api.domain.exploration.repository.ExplorationQueryRepository.ExploreContentRow;
import kr.flint.exploration.domain.UserExplorationProgress;
import kr.flint.exploration.service.ExplorationProgressService;
import kr.flint.infra.storage.cloudfront.CloudFrontUrlProvider;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@Transactional
public class ExplorationQueryFacade {

	// 한 탐색 세션의 고정 크기
	private static final int SESSION_SIZE = 30;

	private final ExplorationQueryRepository explorationQueryRepository;
	private final ExplorationProgressService explorationProgressService;
	private final CloudFrontUrlProvider cloudFrontUrlProvider;

	// 현재 세션을 조회한다. (서버가 진행 상태를 소유)
	public ExplorationSessionRes getSession(Long userId) {
		UserExplorationProgress progress = explorationProgressService.getOrCreate(userId);
		return buildSession(progress);
	}

	// 현재 세션을 끝까지 본 사용자를 다음 세션으로 넘긴다.
	// 다음 세트(30개)가 준비돼 있으면 전진하고, 없으면 End로 기록한다.
	public ExplorationSessionRes advance(Long userId) {
		UserExplorationProgress progress = explorationProgressService.getOrCreate(userId);

		List<ExploreContentRow> rows = explorationQueryRepository.findSession(progress.getSessionCursor(), SESSION_SIZE);
		if (rows.size() < SESSION_SIZE) {
			// 아직 현재 세션조차 없음 → 전진할 것도 없음
			return buildSession(progress);
		}

		Long lastContentId = rows.getLast().contentId();
		if (explorationQueryRepository.existsFullNextSession(lastContentId, SESSION_SIZE)) {
			explorationProgressService.advanceTo(progress, lastContentId);
		} else {
			explorationProgressService.markCompleted(progress);
		}
		return buildSession(progress);
	}

	private ExplorationSessionRes buildSession(UserExplorationProgress progress) {
		List<ExploreContentRow> rows = explorationQueryRepository.findSession(progress.getSessionCursor(), SESSION_SIZE);

		// 완전한 30개를 채우지 못하면 아직 세션이 없는 것(초기 빈 상태)
		if (rows.size() < SESSION_SIZE) {
			return ExplorationSessionRes.empty();
		}

		// 작품별 대표 컬렉션 id (자세히 보기 이동용)
		List<Long> contentIds = rows.stream().map(ExploreContentRow::contentId).toList();
		Map<Long, Long> collectionIdMap = explorationQueryRepository.findRepresentativeCollectionIds(contentIds);

		List<ExploreContentRes> items = rows.stream()
			.map(row -> new ExploreContentRes(
				row.contentId(),
				row.title(),
				row.description(),
				cloudFrontUrlProvider.resolveUrl(row.poster()),
				row.year(),
				collectionIdMap.get(row.contentId())
			))
			.toList();

		Long lastContentId = rows.getLast().contentId();
		boolean hasNext = explorationQueryRepository.existsFullNextSession(lastContentId, SESSION_SIZE);
		ExplorationState state = progress.isCompleted() ? ExplorationState.END : ExplorationState.IN_PROGRESS;
		return ExplorationSessionRes.of(items, state, hasNext);
	}
}
