package kr.flint.api.domain.exploration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import kr.flint.api.domain.exploration.dto.response.ExplorationSessionRes;
import kr.flint.api.domain.exploration.repository.ExplorationQueryRepository;
import kr.flint.api.domain.exploration.repository.ExplorationQueryRepository.ExploreContentRow;
import kr.flint.api.domain.exploration.repository.ExplorationQueryRepository.RepresentativeCollectionRow;
import kr.flint.exploration.domain.UserExplorationProgress;
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
}
