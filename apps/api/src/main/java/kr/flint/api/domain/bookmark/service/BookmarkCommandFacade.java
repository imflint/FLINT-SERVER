package kr.flint.api.domain.bookmark.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.hypersistence.tsid.TSID;
import kr.flint.bookmark.exception.BookmarkErrorCode;
import kr.flint.bookmark.exception.BookmarkException;
import kr.flint.bookmark.repository.CollectionBookmarkRepository;
import kr.flint.bookmark.service.BookmarkCommandService;
import kr.flint.bookmark.service.BookmarkQueryService;
import kr.flint.collection.repository.CollectionRepository;
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
	private final CollectionRepository collectionRepository;

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

    // TODO: 동시성 이슈 처리 필요
	@Transactional
	public boolean toggleCollection(final Long userId, final Long collectionId) {
		userService.getById(userId);

		// 북마크가 되어있는 경우 영향 받은 row 1 -> 북마크 off
		int deleted = collectionBookmarkRepository.deleteCollectionBookmarkByUserIdAndCollectionId(userId, collectionId);
		if(deleted == 1){
			collectionRepository.decBookmarkCount(collectionId);
			return false;
		}

		//북마크가 되어 있지 않은 경우 영향 받은 row 1 -> 북마크 on
		Long id = TSID.Factory.getTsid().toLong();
		int inserted = collectionBookmarkRepository.insertIgnore(id, userId, collectionId);
		if(inserted == 1){
			collectionRepository.incBookmarkCount(collectionId);
			return true;
		}

		// 동시 요청으로 인해 이미 다른 스레드에서 북마크를 한 경우 -> 북마크 on
		return true;
	}


}
