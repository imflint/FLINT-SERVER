package kr.flint.api.domain.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
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
import kr.flint.infra.storage.cloudfront.CloudFrontUrlProvider;

@ExtendWith(MockitoExtension.class)
class SearchQueryFacadeTest {

	@Mock
	private ContentService contentService;

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
		Content content = Content.create(1L, MediaType.MOVIE, "사랑", 2026, "감독", "설명", "poster.jpg");
		when(contentService.getContentByTitle("사랑")).thenReturn(List.of(content));

		List<GetContentSearchRes> result = searchQueryFacade.searchContent("사랑");

		assertThat(result).hasSize(1);
		verify(contentService).getContentByTitle("사랑");
	}

	@Test
	@DisplayName("검색어가 없으면 인기 콘텐츠 30개를 조회")
	void searchContentUsesPopularDefaultList() {
		when(contentService.getPopularContents(30)).thenReturn(List.of());

		assertThat(searchQueryFacade.searchContent(" ")).isEmpty();
		verify(contentService).getPopularContents(30);
	}
}
