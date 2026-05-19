package kr.flint.api.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import kr.flint.api.domain.content.dto.SearchGenre;
import kr.flint.api.domain.content.repository.ContentQueryRepository;
import kr.flint.api.domain.search.dto.response.GetContentSearchRes;
import kr.flint.content.domain.MediaType;
import kr.flint.ott.service.OttService;
import kr.flint.shared.dto.PaginationResponse;

@ExtendWith(MockitoExtension.class)
class ContentQueryFacadeTest {

	@Mock
	private OttService ottService;

	@Mock
	private ContentQueryRepository contentQueryRepository;

	@InjectMocks
	private ContentQueryFacade contentQueryFacade;

	@Nested
	@DisplayName("getContentSearchList")
	class GetContentSearchList {

		@Test
		@DisplayName("검색 조건을 모두 전달하고 size 초과 결과로 다음 커서를 만든다")
		void passesAllConditionsAndPaginates() {
			// given
			GetContentSearchRes first = GetContentSearchRes.of(1L, "눈물 액션 로맨스", "감독", "poster.jpg", 2026);
			GetContentSearchRes second = GetContentSearchRes.of(2L, "눈물 액션 로맨스 2", "감독", "poster.jpg", 2026);
			when(contentQueryRepository.searchContents("눈물", List.of("액션", "로맨스"), MediaType.TV, 1, 1))
				.thenReturn(List.of(first, second));

			// when
			PaginationResponse<GetContentSearchRes> response = contentQueryFacade.getContentSearchList(
				"눈물",
				List.of(SearchGenre.ACTION, SearchGenre.ROMANCE),
				MediaType.TV,
				1,
				1
			);

			// then
			assertThat(response.data()).containsExactly(first);
			assertThat(response.meta().nextCursor()).isEqualTo("2");
			verify(contentQueryRepository).searchContents("눈물", List.of("액션", "로맨스"), MediaType.TV, 1, 1);
		}

		@Test
		@DisplayName("중복 genre는 제거하고 조회한다")
		@SuppressWarnings("unchecked")
		void duplicatedGenresAreDeduplicated() {
			// when
			contentQueryFacade.getContentSearchList(
				null,
				List.of(SearchGenre.ACTION, SearchGenre.ACTION),
				null,
				1,
				20
			);

			// then
			ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
			verify(contentQueryRepository).searchContents(
				ArgumentMatchers.<String>isNull(),
				captor.capture(),
				ArgumentMatchers.<MediaType>isNull(),
				eq(1),
				eq(20)
			);
			assertThat(captor.getValue()).containsExactly("액션");
		}

		@Test
		@DisplayName("조건이 없으면 빈 장르 목록과 전체 mediaType으로 조회한다")
		void searchesAllContentsWithoutConditions() {
			// when
			contentQueryFacade.getContentSearchList(null, null, null, 1, 20);

			// then
			verify(contentQueryRepository).searchContents(
				ArgumentMatchers.<String>isNull(),
				eq(List.of()),
				ArgumentMatchers.<MediaType>isNull(),
				eq(1),
				eq(20)
			);
		}
	}
}
