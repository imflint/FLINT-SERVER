package kr.flint.api.domain.bookmark.service;

import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import kr.flint.api.domain.bookmark.dto.response.GetBookmarkUserRes;
import kr.flint.infra.gpt.dto.GptKeywordDto;
import kr.flint.infra.gpt.dto.TasteWorkMetaDto;
import kr.flint.api.domain.bookmark.repository.BookmarkQueryRepository;
import kr.flint.bookmark.service.BookmarkQueryService;
import kr.flint.infra.gpt.service.ChatService;
import kr.flint.user.dto.response.UserSimpleRes;
import kr.flint.user.service.UserService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class BookmarkQueryFacade {
	private final UserService userService;
	private final BookmarkQueryService bookmarkQueryService;

	public GetBookmarkUserRes getBookmarkedUser(Long collectionId){
		int bookmarkCount = bookmarkQueryService.getBookmarkCount(collectionId);
		List<Long> userIdList = bookmarkQueryService.getBookmarkUserId(collectionId);

		List<UserSimpleRes> userList = userService.getUserInfoList(userIdList);

		return GetBookmarkUserRes.of(bookmarkCount, userList);
	}
}
