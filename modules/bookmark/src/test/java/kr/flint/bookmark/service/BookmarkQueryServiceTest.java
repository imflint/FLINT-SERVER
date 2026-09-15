package kr.flint.bookmark.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import kr.flint.bookmark.repository.CollectionBookmarkRepository;
import kr.flint.bookmark.repository.ContentBookmarkRepository;

@ExtendWith(MockitoExtension.class)
class BookmarkQueryServiceTest {

	@Mock private CollectionBookmarkRepository collectionBookmarkRepository;
	@Mock private ContentBookmarkRepository contentBookmarkRepository;

	@Test
	@DisplayName("응답 대상 컬렉션 ID에 대해서만 사용자 저장 상태를 일괄 조회")
	void getsBookmarkedIdsWithinCandidates() {
		BookmarkQueryService service = new BookmarkQueryService(
			collectionBookmarkRepository,
			contentBookmarkRepository
		);
		when(collectionBookmarkRepository.findCollectionIdsByUserIdAndCollectionIdIn(
			1L,
			List.of(10L, 20L)
		)).thenReturn(List.of(20L));

		assertThat(service.getBookmarkedCollectionIdSet(1L, List.of(10L, 20L)))
			.containsExactly(20L);
		verify(collectionBookmarkRepository)
			.findCollectionIdsByUserIdAndCollectionIdIn(1L, List.of(10L, 20L));
	}

	@Test
	@DisplayName("익명 사용자는 저장 상태 쿼리를 실행하지 않음")
	void anonymousUserSkipsLookup() {
		BookmarkQueryService service = new BookmarkQueryService(
			collectionBookmarkRepository,
			contentBookmarkRepository
		);

		assertThat(service.getBookmarkedCollectionIdSet(null, List.of(10L))).isEmpty();
		verify(collectionBookmarkRepository, never())
			.findCollectionIdsByUserIdAndCollectionIdIn(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyList());
	}
}
