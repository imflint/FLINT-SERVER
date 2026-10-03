package kr.flint.api.domain.bookmark.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import kr.flint.bookmark.exception.BookmarkErrorCode;
import kr.flint.bookmark.exception.BookmarkException;
import kr.flint.bookmark.repository.CollectionBookmarkRepository;
import kr.flint.bookmark.service.BookmarkCommandService;
import kr.flint.bookmark.service.BookmarkQueryService;
import kr.flint.collection.domain.Collection;
import kr.flint.collection.service.CollectionService;
import kr.flint.content.service.ContentService;
import kr.flint.user.service.UserService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class BookmarkCommandFacade {
	// 북마크한 작품 최소 보유 수 — 이 수 이하로 내려가는 취소(토글 OFF)는 차단한다.
	private static final int MIN_CONTENT_BOOKMARK = 5;

	private final BookmarkCommandService bookmarkCommandService;
	private final BookmarkQueryService bookmarkQueryService;
	private final ContentService contentService;
	private final CollectionService collectionService;
	private final UserService userService;
	private final CollectionBookmarkRepository collectionBookmarkRepository;

	@Transactional
	public boolean toggleContent(final Long userId, final Long contentId) {
		userService.getById(userId);

		// 이미 북마크된 작품의 취소 요청이고, 보유 수가 최소치 이하라면 취소를 막는다.
		if (bookmarkQueryService.isContentBookmarked(userId, contentId)
			&& bookmarkQueryService.getContentBookmarkCount(userId) <= MIN_CONTENT_BOOKMARK) {
			throw new BookmarkException(BookmarkErrorCode.CONTENT_BOOKMARK_MIN_LIMIT);
		}

		boolean isBookmarked = bookmarkCommandService.toggleContent(userId, contentId);

		if (isBookmarked) {
			contentService.increaseBookmarkCount(contentId);
			// 키워드 재계산 가능 시점 판정용 카운터 — 토글 OFF는 카운트하지 않음 (스펙: "20개 이상 새롭게 누적")
			userService.incrementContentBookmarkCounter(userId);
		}
		else {contentService.decreaseBookmarkCount(contentId);}

		return isBookmarked;
	}

	@Transactional(isolation = Isolation.READ_COMMITTED)
	public boolean toggleCollection(final Long userId, final Long collectionId) {
		userService.getByIdForUpdate(userId);
		Collection collection = collectionService.getActiveCollectionByIdForUpdate(collectionId);
		boolean isBookmarked = bookmarkCommandService.toggleCollection(userId, collectionId);
		collection.synchronizeBookmarkCount(collectionBookmarkRepository.countByCollectionId(collectionId));
		return isBookmarked;
	}


}
