package kr.flint.api.domain.bookmark.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import kr.flint.bookmark.repository.CollectionBookmarkRepository;
import kr.flint.bookmark.service.BookmarkCommandService;
import kr.flint.bookmark.service.BookmarkQueryService;
import kr.flint.collection.domain.Collection;
import kr.flint.collection.service.CollectionService;
import kr.flint.content.service.ContentService;
import kr.flint.user.service.UserService;

@ExtendWith(MockitoExtension.class)
class BookmarkCommandFacadeTest {

	@Mock private BookmarkCommandService bookmarkCommandService;
	@Mock private BookmarkQueryService bookmarkQueryService;
	@Mock private ContentService contentService;
	@Mock private CollectionService collectionService;
	@Mock private UserService userService;
	@Mock private CollectionBookmarkRepository collectionBookmarkRepository;

	@InjectMocks private BookmarkCommandFacade bookmarkCommandFacade;

	@Test
	@DisplayName("컬렉션 저장 후 실제 관계 수를 카운트에 반영")
	void toggleCollectionSynchronizesInsertedRelationCount() {
		Collection collection = Collection.create("제목", "설명", null, true, 2L);
		when(collectionService.getActiveCollectionByIdForUpdate(10L)).thenReturn(collection);
		when(bookmarkCommandService.toggleCollection(1L, 10L)).thenReturn(true);
		when(collectionBookmarkRepository.countByCollectionId(10L)).thenReturn(1);

		boolean result = bookmarkCommandFacade.toggleCollection(1L, 10L);

		assertThat(result).isTrue();
		assertThat(collection.getBookmarkCount()).isEqualTo(1);
		var order = inOrder(userService, collectionService, bookmarkCommandService, collectionBookmarkRepository);
		order.verify(userService).getByIdForUpdate(1L);
		order.verify(collectionService).getActiveCollectionByIdForUpdate(10L);
		order.verify(bookmarkCommandService).toggleCollection(1L, 10L);
		order.verify(collectionBookmarkRepository).countByCollectionId(10L);
	}

	@Test
	@DisplayName("컬렉션 저장 취소 후 실제 관계 수를 카운트에 반영")
	void toggleCollectionSynchronizesDeletedRelationCount() {
		Collection collection = Collection.create("제목", "설명", null, true, 2L);
		collection.synchronizeBookmarkCount(2);
		when(collectionService.getActiveCollectionByIdForUpdate(10L)).thenReturn(collection);
		when(bookmarkCommandService.toggleCollection(1L, 10L)).thenReturn(false);
		when(collectionBookmarkRepository.countByCollectionId(10L)).thenReturn(1);

		boolean result = bookmarkCommandFacade.toggleCollection(1L, 10L);

		assertThat(result).isFalse();
		assertThat(collection.getBookmarkCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("관계 무결성 오류를 저장 상태 응답으로 숨기지 않음")
	void toggleCollectionPropagatesIntegrityFailure() {
		Collection collection = Collection.create("제목", "설명", null, true, 2L);
		collection.synchronizeBookmarkCount(2);
		when(collectionService.getActiveCollectionByIdForUpdate(10L)).thenReturn(collection);
		doThrow(new DataIntegrityViolationException("invalid relationship"))
			.when(bookmarkCommandService).toggleCollection(1L, 10L);

		assertThatThrownBy(() -> bookmarkCommandFacade.toggleCollection(1L, 10L))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(collection.getBookmarkCount()).isEqualTo(2);
	}
}
