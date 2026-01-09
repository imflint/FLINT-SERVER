package kr.flint.api.bookmark.service;

import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.flint.bookmark.service.BookmarkService;
import kr.flint.collection.service.CollectionService;
import kr.flint.content.service.ContentService;
import kr.flint.user.service.UserService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class BookmarkCommandFacade {
	private final BookmarkService bookmarkService;
	private final ContentService contentService;
	private final CollectionService collectionService;
	private final UserService userService;

	@Transactional
	public boolean toggleContent(final Long userId, final Long contentId) {
		userService.getById(userId);
		boolean isBookmarked = bookmarkService.toggleContent(userId, contentId);

		if (isBookmarked) {contentService.increaseBookmarkCount(contentId);}
		else {collectionService.decreaseBookmarkCount(contentId);}

		return isBookmarked;
	}

	@Transactional
	public boolean toggleCollection(final Long userId, final Long collectionId) {
		userService.getById(userId);
		boolean isBookmarked = bookmarkService.toggleCollection(userId, collectionId);

		if (isBookmarked) {collectionService.increaseBookmarkCount(collectionId);}
		else {collectionService.decreaseBookmarkCount(collectionId);}

		return isBookmarked;
	}

}
