package kr.flint.api.domain.exploration.service;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import kr.flint.api.domain.exploration.dto.response.ExploreContentRes;
import kr.flint.api.domain.exploration.dto.response.ExplorationSessionRes;
import kr.flint.api.domain.exploration.repository.ExplorationQueryRepository;
import kr.flint.api.domain.exploration.repository.ExplorationQueryRepository.ExploreContentRow;
import kr.flint.infra.storage.cloudfront.CloudFrontUrlProvider;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExplorationQueryFacade {

	// 한 탐색 세션의 고정 크기
	private static final int SESSION_SIZE = 30;

	private final ExplorationQueryRepository explorationQueryRepository;
	private final CloudFrontUrlProvider cloudFrontUrlProvider;

	public ExplorationSessionRes getSession(Long cursor) {
		List<ExploreContentRow> rows = explorationQueryRepository.findSession(cursor, SESSION_SIZE);

		// 완전한 30개를 채우지 못하면 세션을 만들지 않고 End(-1)로 반환한다.
		if (rows.size() < SESSION_SIZE) {
			return ExplorationSessionRes.end();
		}

		List<ExploreContentRes> items = rows.stream()
			.map(row -> new ExploreContentRes(
				row.contentId(),
				row.title(),
				cloudFrontUrlProvider.resolveUrl(row.poster()),
				row.year()
			))
			.toList();

		// 다음 세션 요청용 커서 = 이번 세션 마지막 작품 id
		long nextCursor = rows.getLast().contentId();
		return ExplorationSessionRes.of(items, nextCursor);
	}
}
