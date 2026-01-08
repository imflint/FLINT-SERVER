package kr.flint.api.bookmark.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.flint.bookmark.service.BookmarkService;
import kr.flint.user.service.UserService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookmarkCommandFacade {
	private final BookmarkService bookmarkService;
	private final UserService userService;

	@Transactional
	public boolean toggleContent(final Long userId, final Long contentId) {
		userService.getById(userId);
		return bookmarkService.toggleContent(userId, contentId);
	}

	@Transactional
	public boolean toggleCollection(final Long userId, final Long collectionId) {
		userService.getById(userId);
		return bookmarkService.toggleCollection(userId, collectionId);
	}

}
