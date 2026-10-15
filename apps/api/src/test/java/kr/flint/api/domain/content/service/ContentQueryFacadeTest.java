package kr.flint.api.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

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
import kr.flint.api.domain.content.dto.GetBookmarkedContentCountRes;
import kr.flint.api.domain.content.dto.GetContentDetailRes;
import kr.flint.api.domain.content.dto.GetContentListRes;
import kr.flint.api.domain.content.dto.SearchGenre;
import kr.flint.api.domain.content.repository.ContentQueryRepository;
import kr.flint.api.domain.content.repository.ContentQueryRepository.BookmarkedContentRow;
import kr.flint.api.domain.content.repository.ContentQueryRepository.ContentSearchRow;
import kr.flint.api.domain.search.dto.response.GetContentSearchRes;
import kr.flint.bookmark.service.BookmarkQueryService;
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

	@Mock
	private BookmarkQueryService bookmarkQueryService;

	@InjectMocks
	private ContentQueryFacade contentQueryFacade;

	@Nested
	@DisplayName("getUserBookmarkedContentList")
	class GetUserBookmarkedContentList {

		@Test
		@DisplayName("로그인한 사용자의 콘텐츠 북마크 여부를 함께 반환한다")
		void returnsCurrentUserBookmarkState() {
			// given
			List<GetContentDetailRes> contents = List.of(
				content(1L, "첫 번째"),
				content(2L, "두 번째")
			);
			when(contentQueryRepository.getContentDetailList(10L))
				.thenReturn(contents);
			when(bookmarkQueryService.getBookmarkedContentIdSet(20L, List.of(1L, 2L)))
				.thenReturn(Set.of(2L));

			// when
			GetContentListRes response =
				contentQueryFacade.getUserBookmarkedContentList(20L, 10L);

			// then
			assertThat(response.totalCount()).isEqualTo(2);
			assertThat(response.contents())
				.extracting(GetContentListRes.Content::isBookmarked)
				.containsExactly(false, true);
			assertThat(response.contents())
				.extracting(GetContentListRes.Content::author)
				.containsExactly("크리스토퍼 놀란", "크리스토퍼 놀란");
			verify(contentQueryRepository).getContentDetailList(10L);
			verify(bookmarkQueryService).getBookmarkedContentIdSet(20L, List.of(1L, 2L));
		}

		@Test
		@DisplayName("대상 사용자의 북마크 콘텐츠가 없으면 북마크 여부 조회를 생략한다")
		void skipsBookmarkStateLookupWhenEmpty() {
			// given
			when(contentQueryRepository.getContentDetailList(10L))
				.thenReturn(List.of());

			// when
			GetContentListRes response =
				contentQueryFacade.getUserBookmarkedContentList(20L, 10L);

			// then
			assertThat(response.totalCount()).isZero();
			assertThat(response.contents()).isEmpty();
			verify(contentQueryRepository).getContentDetailList(10L);
			verifyNoInteractions(bookmarkQueryService);
		}

		private GetContentDetailRes content(Long id, String title) {
			return new GetContentDetailRes(
				id,
				title,
				"크리스토퍼 놀란",
				"poster.jpg",
				2026,
				5,
				List.of()
			);
		}
	}

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
				"크리스토퍼 놀란",
				"poster.jpg",
				2026,
				5,
				List.of()
			);
		}
	}

	@Nested
	@DisplayName("getBookmarkedContentCount")
	class GetBookmarkedContentCount {

		@Test
		@DisplayName("로그인한 사용자의 북마크 콘텐츠 전체 개수를 반환한다")
		void returnsBookmarkedContentCount() {
			// given
			when(bookmarkQueryService.getContentBookmarkCount(1L)).thenReturn(12);

			// when
			GetBookmarkedContentCountRes response = contentQueryFacade.getBookmarkedContentCount(1L);

			// then
			assertThat(response.totalCount()).isEqualTo(12);
			verify(bookmarkQueryService).getContentBookmarkCount(1L);
		}
	}

	@Nested
	@DisplayName("getContentSearchList")
	class GetContentSearchList {

		@Test
		@DisplayName("검색 조건을 모두 전달하고 size 초과 결과로 다음 커서를 만든다")
		void passesAllConditionsAndPaginates() {
			// given
			ContentSearchRow first = new ContentSearchRow(1L, "눈물 액션 로맨스", "감독", "poster.jpg", 2026, 10, 0, 4.5);
			ContentSearchRow second = new ContentSearchRow(2L, "눈물 액션 로맨스 2", "감독", "poster.jpg", 2026, 9, 1, 3.2);
			ContentSearchCondition condition = ContentSearchCondition.of(
				"눈물",
				"액션",
				MediaType.TV,
				null,
				1
			);
			when(contentQueryRepository.searchContents(condition))
				.thenReturn(List.of(first, second));

			// when
			PaginationResponse<GetContentSearchRes> response = contentQueryFacade.getContentSearchList(
				"눈물",
				SearchGenre.ACTION,
				MediaType.TV,
				null,
				1
			);

			// then
			assertThat(response.data())
				.extracting(GetContentSearchRes::title)
				.containsExactly("눈물 액션 로맨스");
			assertThat(response.meta().nextCursor()).isEqualTo(ContentSearchCursor.keyword(0, 4.5, 1L, true, true).encode());
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
		@DisplayName("localized 검색은 결과 조회 전에 이전 keyword 커서를 거절한다")
		void rejectsLegacyKeywordCursorBeforeSearch() {
			String cursor = ContentSearchCursor.keyword(0, 1.0, 1L).encode();
			assertThatThrownBy(() -> contentQueryFacade.getContentSearchList("해리", SearchGenre.ACTION, null, cursor, 20))
				.isInstanceOf(GeneralException.class)
				.hasMessageContaining("cursor 형식이 올바르지 않습니다.");
			verify(contentQueryRepository, org.mockito.Mockito.never()).searchContents(org.mockito.ArgumentMatchers.any());
		}

		@Test
		void readModelReturnsV3CursorAndRejectsEarlierKeywordVersions() {
			var condition = ContentSearchCondition.of("해리", null, null, null, 1);
			when(contentQueryRepository.searchContents(condition)).thenReturn(List.of(
				new ContentSearchRow(2L, "해리", null, null, 2026, 0, 0, 2.5),
				new ContentSearchRow(1L, "해리 포터", null, null, 2026, 0, 1, 1.5)
			));
			var result = contentQueryFacade.getContentSearchList("해리", null, null, null, 1);
			var cursor = ContentSearchCursor.decode(result.meta().nextCursor());
			assertThat(cursor.version()).isEqualTo(3);
			assertThat(cursor.contentId()).isEqualTo(2L);
			cursor.validateKeywordVersion(true, true);
			for (boolean localized : List.of(false, true)) {
				String oldCursor = ContentSearchCursor.keyword(0, 2.5, 2L, localized).encode();
				assertThatThrownBy(() -> contentQueryFacade.getContentSearchList("해리", null, null, oldCursor, 1))
					.isInstanceOf(GeneralException.class);
			}
			verify(contentQueryRepository).searchContents(condition);
		}

		@Test
		@DisplayName("영문 단어 검색은 v4 커서를 반환하고 이전 커서를 조회 전에 거절한다")
		void englishWordsReturnV4AndRejectV3BeforeReading() {
			when(contentQueryRepository.usesEnglishWords("harry potter")).thenReturn(true);
			var condition = ContentSearchCondition.of("harry potter", null, null, null, 1);
			when(contentQueryRepository.searchContents(condition)).thenReturn(List.of(
				new ContentSearchRow(2L, "Harry Potter", null, null, 2026, 0, 0, 2.5),
				new ContentSearchRow(1L, "Harry Potter 2", null, null, 2026, 0, 1, 1.5)
			));
			var result = contentQueryFacade.getContentSearchList("harry potter", null, null, null, 1);
			var cursor = ContentSearchCursor.decode(result.meta().nextCursor());
			assertThat(cursor.version()).isEqualTo(4);
			assertThat(cursor.contentId()).isEqualTo(2L);
			cursor.validateKeywordVersion(true, true, true);
			String oldCursor = ContentSearchCursor.keyword(0, 2.5, 2L, true, true).encode();
			assertThatThrownBy(() -> contentQueryFacade.getContentSearchList("harry potter", null, null, oldCursor, 1))
				.isInstanceOf(GeneralException.class);
			verify(contentQueryRepository, org.mockito.Mockito.times(1)).searchContents(org.mockito.ArgumentMatchers.any());
		}

		@Test
		@DisplayName("한 글자 문자 검색은 v5를 반환하고 이전 커서를 조회 전에 거절한다")
		void characterSearchReturnsV5AndRejectsOldPagesBeforeReading() {
			when(contentQueryRepository.usesSingleCharacterInfix("봄")).thenReturn(true);
			var condition = ContentSearchCondition.of("봄", null, null, null, 1);
			when(contentQueryRepository.searchContents(condition)).thenReturn(List.of(
				new ContentSearchRow(2L, "봄", null, null, 2026, 0, 0, 0),
				new ContentSearchRow(3L, "봄날", null, null, 2026, 0, 1, 0)
			));
			var result = contentQueryFacade.getContentSearchList("봄", null, null, null, 1);
			var cursor = ContentSearchCursor.decode(result.meta().nextCursor());
			assertThat(cursor.version()).isEqualTo(5);
			assertThat(cursor.exactMatchRank()).isZero();
			assertThat(cursor.relevanceScore()).isZero();
			String old = ContentSearchCursor.keyword(0, 0, 2L, true, true).encode();
			assertThatThrownBy(() -> contentQueryFacade.getContentSearchList("봄", null, null, old, 1))
				.isInstanceOf(GeneralException.class);
			verify(contentQueryRepository, org.mockito.Mockito.times(1)).searchContents(org.mockito.ArgumentMatchers.any());
		}

		@Test
		@DisplayName("문자와 숫자가 없는 keyword는 DB 조회 전에 거절한다")
		void rejectsSymbolOnlyKeyword() {
			for (String keyword : List.of("!!!", " ! ", "\uD83D\uDE00")) {
				assertThatThrownBy(() -> contentQueryFacade.getContentSearchList(keyword, null, null, null, 20))
					.isInstanceOf(GeneralException.class)
					.hasMessageContaining("keyword는 문자 또는 숫자를 1자 이상 포함해야 합니다.");
			}
			verifyNoInteractions(contentQueryRepository);
		}

		@Test
		@DisplayName("공백만 있는 keyword는 검색어 없는 조회로 처리한다")
		void ignoresBlankKeyword() {
			for (String keyword : List.of("   ", "\u00a0", "\u3000", "\u202f")) {
				contentQueryFacade.getContentSearchList(keyword, null, null, null, 20);
			}
			verify(contentQueryRepository, org.mockito.Mockito.times(4))
				.searchContents(eq(ContentSearchCondition.of(null, null, null, null, 20)));
		}

		@Test
		@DisplayName("두 글자 keyword의 앞뒤 공백만 제거하고 조회한다")
		void trimsKeywordBeforeSearching() {
			contentQueryFacade.getContentSearchList(" 눈물 ", null, null, null, 20);
			verify(contentQueryRepository).searchContents(eq(ContentSearchCondition.of("눈물", null, null, null, 20)));
		}

		@Test
		@DisplayName("단일 genre를 DB 장르명으로 변환하고 조회한다")
		void mapsSingleGenreToGenreName() {
			// when
			contentQueryFacade.getContentSearchList(
				null,
				SearchGenre.ACTION,
				null,
				null,
				20
			);

			// then
			ArgumentCaptor<ContentSearchCondition> captor = ArgumentCaptor.forClass(ContentSearchCondition.class);
			verify(contentQueryRepository).searchContents(captor.capture());
			assertThat(captor.getValue().genreName()).isEqualTo("액션");
			assertThat(captor.getValue().cursor()).isNull();
			assertThat(captor.getValue().size()).isEqualTo(20);
		}

		@Test
		@DisplayName("조건이 없으면 장르 필터 없이 전체 mediaType으로 조회한다")
		void searchesAllContentsWithoutConditions() {
			// when
			contentQueryFacade.getContentSearchList(null, null, null, null, 20);

			// then
			verify(contentQueryRepository).searchContents(
				eq(ContentSearchCondition.of(null, null, null, null, 20))
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
				eq(ContentSearchCondition.of(null, null, null, ContentSearchCursor.of(3, 123L), 20))
			);
		}

		@Test
		@DisplayName("keyword 검색에 인기순 cursor를 전달하면 거부")
		void rejectsCursorForDifferentSortMode() {
			String cursor = ContentSearchCursor.popular(3, 123L).encode();

			assertThatThrownBy(() ->
				contentQueryFacade.getContentSearchList("눈물", null, null, cursor, 20))
				.isInstanceOf(GeneralException.class);

			verifyNoInteractions(contentQueryRepository);
		}
	}
}
