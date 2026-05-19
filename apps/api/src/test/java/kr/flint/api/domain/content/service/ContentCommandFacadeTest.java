package kr.flint.api.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import kr.flint.api.domain.content.dto.SearchGenre;
import kr.flint.api.domain.content.repository.ContentQueryRepository;
import kr.flint.api.domain.search.dto.response.GetContentSearchRes;
import kr.flint.content.service.ContentService;
import kr.flint.infra.tmdb.client.TmdbClient;
import kr.flint.shared.dto.PaginationResponse;

@ExtendWith(MockitoExtension.class)
class ContentCommandFacadeTest {

	@Mock
	private ContentService contentService;

	@Mock
	private TmdbClient tmdbClient;

	@Mock
	private OttCommandFacade ottCommandFacade;

	@Mock
	private ContentQueryRepository contentQueryRepository;

	@InjectMocks
	private ContentCommandFacade contentCommandFacade;

	@Nested
	@DisplayName("getContentSearchList")
	class GetContentSearchList {

		@Test
		@DisplayName("genre가 여러 개면 keyword보다 우선하고 AND 검색용 장르명 목록으로 조회")
		void genresTakePriorityOverKeyword() {
			// given
			GetContentSearchRes content = GetContentSearchRes.of(1L, "콘텐츠", "감독", "poster.jpg", 2026);
			when(contentQueryRepository.findPopularByGenreNames(List.of("액션", "로맨스"), 1, 20))
				.thenReturn(List.of(content));

			// when
			PaginationResponse<GetContentSearchRes> response = contentCommandFacade.getContentSearchList(
				"keyword",
				List.of(SearchGenre.ACTION, SearchGenre.ROMANCE),
				1,
				20
			);

			// then
			assertThat(response.data()).containsExactly(content);
			assertThat(response.meta().nextCursor()).isNull();
			verify(tmdbClient, never()).getMultiList(anyString(), anyString(), anyInt());
		}

		@Test
		@DisplayName("중복 genre는 제거하고 조회")
		@SuppressWarnings("unchecked")
		void duplicatedGenresAreDeduplicated() {
			// given
			when(contentQueryRepository.findPopularByGenreNames(List.of("액션"), 1, 20))
				.thenReturn(List.of());

			// when
			contentCommandFacade.getContentSearchList(
				null,
				List.of(SearchGenre.ACTION, SearchGenre.ACTION),
				1,
				20
			);

			// then
			ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
			verify(contentQueryRepository).findPopularByGenreNames(captor.capture(), eq(1), eq(20));
			assertThat(captor.getValue()).containsExactly("액션");
		}
	}
}
