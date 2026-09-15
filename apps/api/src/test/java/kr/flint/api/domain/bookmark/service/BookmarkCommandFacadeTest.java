package kr.flint.api.domain.bookmark.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import kr.flint.bookmark.domain.CollectionBookmark;
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
		when(collectionBookmarkRepository.deleteCollectionBookmarkByUserIdAndCollectionId(1L, 10L)).thenReturn(0);
		when(collectionBookmarkRepository.insertIgnore(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(10L)))
			.thenReturn(1);
		when(collectionBookmarkRepository.countByCollectionId(10L)).thenReturn(1);

		boolean result = bookmarkCommandFacade.toggleCollection(1L, 10L);

		assertThat(result).isTrue();
		assertThat(collection.getBookmarkCount()).isEqualTo(1);
		verify(collectionService).getActiveCollectionByIdForUpdate(10L);
	}

	@Test
	@DisplayName("컬렉션 저장 취소 후 실제 관계 수를 카운트에 반영")
	void toggleCollectionSynchronizesDeletedRelationCount() {
		Collection collection = Collection.create("제목", "설명", null, true, 2L);
		collection.synchronizeBookmarkCount(2);
		when(collectionService.getActiveCollectionByIdForUpdate(10L)).thenReturn(collection);
		when(collectionBookmarkRepository.deleteCollectionBookmarkByUserIdAndCollectionId(1L, 10L)).thenReturn(1);
		when(collectionBookmarkRepository.countByCollectionId(10L)).thenReturn(1);

		boolean result = bookmarkCommandFacade.toggleCollection(1L, 10L);

		assertThat(result).isFalse();
		assertThat(collection.getBookmarkCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("동시 삽입으로 INSERT IGNORE가 0이어도 실제 관계가 있으면 저장 상태 true")
	void toggleCollectionReturnsActualStateAfterIgnoredInsert() {
		Collection collection = Collection.create("제목", "설명", null, true, 2L);
		when(collectionService.getActiveCollectionByIdForUpdate(10L)).thenReturn(collection);
		when(collectionBookmarkRepository.deleteCollectionBookmarkByUserIdAndCollectionId(1L, 10L)).thenReturn(0);
		when(collectionBookmarkRepository.insertIgnore(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(10L)))
			.thenReturn(0);
		when(collectionBookmarkRepository.countByCollectionId(10L)).thenReturn(1);
		when(collectionBookmarkRepository.findByCollectionIdAndUserId(10L, 1L))
			.thenReturn(Optional.of(CollectionBookmark.create(1L, 10L)));

		assertThat(bookmarkCommandFacade.toggleCollection(1L, 10L)).isTrue();
		assertThat(collection.getBookmarkCount()).isEqualTo(1);
	}
}
