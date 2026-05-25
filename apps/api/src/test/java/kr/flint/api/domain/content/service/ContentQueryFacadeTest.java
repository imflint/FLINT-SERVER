package kr.flint.api.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

import kr.flint.api.domain.content.dto.ContentSearchCondition;
import kr.flint.api.domain.content.dto.SearchGenre;
import kr.flint.api.domain.content.repository.ContentQueryRepository;
import kr.flint.api.domain.content.repository.ContentQueryRepository.ContentSearchRow;
import kr.flint.api.domain.search.dto.response.GetContentSearchRes;
import kr.flint.content.domain.MediaType;
import kr.flint.ott.service.OttService;
import kr.flint.shared.dto.PaginationResponse;
import kr.flint.shared.exception.GeneralException;

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
			ContentSearchRow first = new ContentSearchRow(1L, "눈물 액션 로맨스", "감독", "poster.jpg", 2026, 10);
			ContentSearchRow second = new ContentSearchRow(2L, "눈물 액션 로맨스 2", "감독", "poster.jpg", 2026, 9);
			ContentSearchCondition condition = ContentSearchCondition.of(
				"눈물",
				List.of("액션", "로맨스"),
				MediaType.TV,
				1,
				1
			);
			when(contentQueryRepository.searchContents(condition))
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
			assertThat(response.data())
				.extracting(GetContentSearchRes::title)
				.containsExactly("눈물 액션 로맨스");
			assertThat(response.meta().nextCursor()).isEqualTo("2");
			verify(contentQueryRepository).searchContents(condition);
		}

		@Test
		@DisplayName("cursor가 1보다 작으면 검색하지 않고 예외를 던진다")
		void rejectsInvalidCursor() {
			// when
			assertThatThrownBy(() -> contentQueryFacade.getContentSearchList(null, null, null, 0, 20))
				.isInstanceOf(GeneralException.class)
				.hasMessageContaining("cursor는 1 이상이어야 합니다.");

			// then
			verifyNoInteractions(contentQueryRepository);
		}

		@Test
		@DisplayName("size가 1보다 작으면 검색하지 않고 예외를 던진다")
		void rejectsInvalidSize() {
			// when
			assertThatThrownBy(() -> contentQueryFacade.getContentSearchList(null, null, null, 1, 0))
				.isInstanceOf(GeneralException.class)
				.hasMessageContaining("size는 1 이상이어야 합니다.");

			// then
			verifyNoInteractions(contentQueryRepository);
		}

		@Test
		@DisplayName("size가 최대값보다 크면 검색하지 않고 예외를 던진다")
		void rejectsTooLargeSize() {
			// when
			assertThatThrownBy(() -> contentQueryFacade.getContentSearchList(null, null, null, 1, 51))
				.isInstanceOf(GeneralException.class)
				.hasMessageContaining("size는 50 이하여야 합니다.");

			// then
			verifyNoInteractions(contentQueryRepository);
		}

		@Test
		@DisplayName("keyword가 1자여도 기존 계약대로 조회한다")
		void acceptsOneCharacterKeyword() {
			// when
			contentQueryFacade.getContentSearchList("눈", null, null, 1, 20);

			// then
			verify(contentQueryRepository).searchContents(
				eq(ContentSearchCondition.of("눈", List.of(), null, 1, 20))
			);
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
			ArgumentCaptor<ContentSearchCondition> captor = ArgumentCaptor.forClass(ContentSearchCondition.class);
			verify(contentQueryRepository).searchContents(captor.capture());
			assertThat(captor.getValue().genreNames()).containsExactly("액션");
			assertThat(captor.getValue().page()).isEqualTo(1);
			assertThat(captor.getValue().size()).isEqualTo(20);
		}

		@Test
		@DisplayName("조건이 없으면 빈 장르 목록과 전체 mediaType으로 조회한다")
		void searchesAllContentsWithoutConditions() {
			// when
			contentQueryFacade.getContentSearchList(null, null, null, 1, 20);

			// then
			verify(contentQueryRepository).searchContents(
				eq(ContentSearchCondition.of(null, List.of(), null, 1, 20))
			);
		}
	}
}
