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
import kr.flint.api.domain.content.dto.ContentSearchCursor;
import kr.flint.api.domain.content.dto.GetContentDetailRes;
import kr.flint.api.domain.content.dto.SearchGenre;
import kr.flint.api.domain.content.repository.ContentQueryRepository;
import kr.flint.api.domain.content.repository.ContentQueryRepository.BookmarkedContentRow;
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
	@DisplayName("getBookmarkedContentList")
	class GetBookmarkedContentList {

		@Test
		@DisplayName("size 초과 결과로 다음 커서를 만든다")
		void paginatesBookmarkedContents() {
			// given
			BookmarkedContentRow first = row(30L, 1L, "첫 번째");
			BookmarkedContentRow second = row(20L, 2L, "두 번째");
			BookmarkedContentRow third = row(10L, 3L, "세 번째");
			when(contentQueryRepository.getBookmarkedContentRows(1L, null, 3))
				.thenReturn(List.of(first, second, third));

			// when
			PaginationResponse<GetContentDetailRes> response =
				contentQueryFacade.getBookmarkedContentList(1L, null, 2);

			// then
			assertThat(response.data())
				.extracting(GetContentDetailRes::title)
				.containsExactly("첫 번째", "두 번째");
			assertThat(response.meta().returned()).isEqualTo(2);
			assertThat(response.meta().nextCursor()).isEqualTo("20");
			verify(contentQueryRepository).getBookmarkedContentRows(1L, null, 3);
		}

		@Test
		@DisplayName("cursor와 size를 조회 조건으로 전달한다")
		void passesCursorAndSize() {
			// when
			contentQueryFacade.getBookmarkedContentList(1L, 20L, 10);

			// then
			verify(contentQueryRepository).getBookmarkedContentRows(1L, 20L, 11);
		}

		private BookmarkedContentRow row(Long bookmarkId, Long contentId, String title) {
			return new BookmarkedContentRow(
				bookmarkId,
				contentId,
				title,
				"poster.jpg",
				2026,
				5,
				List.of()
			);
		}
	}

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
				null,
				1
			);
			when(contentQueryRepository.searchContents(condition))
				.thenReturn(List.of(first, second));

			// when
			PaginationResponse<GetContentSearchRes> response = contentQueryFacade.getContentSearchList(
				"눈물",
				List.of(SearchGenre.ACTION, SearchGenre.ROMANCE),
				MediaType.TV,
				null,
				1
			);

			// then
			assertThat(response.data())
				.extracting(GetContentSearchRes::title)
				.containsExactly("눈물 액션 로맨스");
			assertThat(response.meta().nextCursor()).isEqualTo(ContentSearchCursor.of(10, 1L).encode());
			verify(contentQueryRepository).searchContents(condition);
		}

		@Test
		@DisplayName("cursor 형식이 올바르지 않으면 검색하지 않고 예외를 던진다")
		void rejectsInvalidCursor() {
			// when
			assertThatThrownBy(() -> contentQueryFacade.getContentSearchList(null, null, null, "invalid", 20))
				.isInstanceOf(GeneralException.class)
				.hasMessageContaining("cursor 형식이 올바르지 않습니다.");

			// then
			verifyNoInteractions(contentQueryRepository);
		}

		@Test
		@DisplayName("keyword가 1자여도 기존 계약대로 조회한다")
		void acceptsOneCharacterKeyword() {
			// when
			contentQueryFacade.getContentSearchList("눈", null, null, null, 20);

			// then
			verify(contentQueryRepository).searchContents(
				eq(ContentSearchCondition.of("눈", List.of(), null, null, 20))
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
				null,
				20
			);

			// then
			ArgumentCaptor<ContentSearchCondition> captor = ArgumentCaptor.forClass(ContentSearchCondition.class);
			verify(contentQueryRepository).searchContents(captor.capture());
			assertThat(captor.getValue().genreNames()).containsExactly("액션");
			assertThat(captor.getValue().cursor()).isNull();
			assertThat(captor.getValue().size()).isEqualTo(20);
		}

		@Test
		@DisplayName("조건이 없으면 빈 장르 목록과 전체 mediaType으로 조회한다")
		void searchesAllContentsWithoutConditions() {
			// when
			contentQueryFacade.getContentSearchList(null, null, null, null, 20);

			// then
			verify(contentQueryRepository).searchContents(
				eq(ContentSearchCondition.of(null, List.of(), null, null, 20))
			);
		}

		@Test
		@DisplayName("cursor token을 검색 조건으로 전달한다")
		void passesDecodedCursor() {
			// given
			String cursor = ContentSearchCursor.of(3, 123L).encode();

			// when
			contentQueryFacade.getContentSearchList(null, null, null, cursor, 20);

			// then
			verify(contentQueryRepository).searchContents(
				eq(ContentSearchCondition.of(null, List.of(), null, ContentSearchCursor.of(3, 123L), 20))
			);
		}
	}
}
