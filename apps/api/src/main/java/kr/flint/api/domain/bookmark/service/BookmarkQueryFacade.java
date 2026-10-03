package kr.flint.api.domain.bookmark.service;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import kr.flint.api.domain.bookmark.dto.response.GetBookmarkUserRes;
import kr.flint.infra.gpt.dto.GptKeywordDto;
import kr.flint.infra.gpt.dto.TasteWorkMetaDto;
import kr.flint.api.domain.bookmark.repository.BookmarkQueryRepository;
import kr.flint.bookmark.service.BookmarkQueryService;
import kr.flint.infra.gpt.service.ChatService;
import kr.flint.infra.storage.cloudfront.CloudFrontUrlProvider;
import kr.flint.user.dto.response.UserSimpleRes;
import kr.flint.user.service.UserService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class BookmarkQueryFacade {
	private final UserService userService;
	private final BookmarkQueryService bookmarkQueryService;
	private final CloudFrontUrlProvider cloudFrontUrlProvider;

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public GetBookmarkUserRes getBookmarkedUser(Long collectionId){
		int bookmarkCount = bookmarkQueryService.getBookmarkCount(collectionId);
		List<Long> userIdList = bookmarkQueryService.getBookmarkUserId(collectionId);

		List<UserSimpleRes> userList = userIdList.isEmpty() ? List.of() : userService.getUserInfoList(userIdList)
			.stream()
			.map(user -> new UserSimpleRes(
				user.userId(),
				user.nickName(),
				cloudFrontUrlProvider.resolveUrl(user.profileImageUrl()),
				user.userRole()
			))
			.toList();

		return GetBookmarkUserRes.of(bookmarkCount, userList);
	}
}
