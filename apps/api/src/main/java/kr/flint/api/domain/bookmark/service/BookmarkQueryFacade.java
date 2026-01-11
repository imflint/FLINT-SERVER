package kr.flint.api.domain.bookmark.service;

import java.util.List;

import org.springframework.stereotype.Component;

import kr.flint.api.domain.bookmark.dto.response.GetBookmarkUserRes;
import kr.flint.bookmark.service.BookmarkService;
import kr.flint.user.dto.response.UserSimpleRes;
import kr.flint.user.service.UserService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class BookmarkQueryFacade {
	private final UserService userService;
	private final BookmarkService bookmarkService;

	public GetBookmarkUserRes getBookmarkedUser(Long collectionId){
		int bookmarkCount = bookmarkService.getBookmarkCount(collectionId);
		List<Long> userIdList = bookmarkService.getBookmarkUserId(collectionId);

		List<UserSimpleRes> userList = userService.getUserInfoList(userIdList);

		return GetBookmarkUserRes.of(bookmarkCount, userList);
	}
}
