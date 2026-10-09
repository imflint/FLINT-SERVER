package kr.flint.api.domain.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import kr.flint.api.domain.home.repository.HomeCollectionRepository;
import kr.flint.api.domain.search.dto.response.GetContentSearchRes;
import kr.flint.api.domain.search.repository.SearchQueryRepository;
import kr.flint.content.domain.Content;
import kr.flint.content.domain.MediaType;
import kr.flint.content.service.ContentService;
import kr.flint.api.domain.content.repository.ContentSearchNativeRepository;
import kr.flint.api.domain.content.repository.ContentSearchProjection;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import kr.flint.infra.storage.cloudfront.CloudFrontUrlProvider;
import kr.flint.shared.exception.GeneralException;

@ExtendWith(MockitoExtension.class)
class SearchQueryFacadeTest {

	@Mock
	private ContentService contentService;
	@Mock
	private ContentSearchNativeRepository contentSearchNativeRepository;

	@Mock
	private SearchQueryRepository searchQueryRepository;

	@Mock
	private HomeCollectionRepository homeCollectionRepository;

	@Mock
	private CloudFrontUrlProvider cloudFrontUrlProvider;

	@InjectMocks
	private SearchQueryFacade searchQueryFacade;

	@Test
	@DisplayName("온보딩 콘텐츠 검색은 키워드 결과를 제한하지 않음")
	void searchContentDoesNotLimitKeywordResults() {
		ContentSearchProjection row = new SpelAwareProxyProjectionFactory().createProjection(ContentSearchProjection.class,
			java.util.Map.of("id",1L,"title","사랑","year",2026,"bookmarkCount",0,"exactMatchRank",0,"relevanceScore",1.0));
		when(contentSearchNativeRepository.searchAllKeywords("사랑")).thenReturn(List.of(row));

		List<GetContentSearchRes> result = searchQueryFacade.searchContent("사랑");

		assertThat(result).hasSize(1);
		verify(contentSearchNativeRepository).searchAllKeywords("사랑");
	}

	@Test
	@DisplayName("검색어가 없으면 인기 콘텐츠 30개를 조회")
	void searchContentUsesPopularDefaultList() {
		when(contentService.getPopularContents(30)).thenReturn(List.of());

		assertThat(searchQueryFacade.searchContent(" ")).isEmpty();
		verify(contentService).getPopularContents(30);
	}

	@Test
	@DisplayName("레거시 검색도 정규화 후 두 글자 미만은 조회 전에 거절한다")
	void rejectsNormalizedShortKeyword() {
		for (String keyword : List.of("Ａ!", "해🔥", "!!!")) {
			assertThatThrownBy(() -> searchQueryFacade.searchContent(keyword))
				.isInstanceOf(GeneralException.class)
				.hasMessageContaining("keyword는 2자 이상이어야 합니다.");
		}
		verifyNoInteractions(contentService, contentSearchNativeRepository);
	}
}
