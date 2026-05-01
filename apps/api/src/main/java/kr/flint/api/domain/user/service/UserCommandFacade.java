package kr.flint.api.domain.user.service;

import java.util.List;

import kr.flint.api.domain.bookmark.repository.BookmarkQueryRepository;
import kr.flint.infra.gpt.dto.TasteWorkMetaDto;
import kr.flint.infra.gpt.service.ChatService;
import kr.flint.taste.dto.response.KeywordSimpleRes;
import kr.flint.taste.service.TasteService;
import kr.flint.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserCommandFacade {

    private final UserService userService;
	private final ChatService chatService;
	private final TasteService tasteService;
	private final BookmarkQueryRepository bookmarkQueryRepository;

	public void callGpt(Long userId) {
		List<TasteWorkMetaDto> tasteWorkMetaDto = bookmarkQueryRepository.getBookmarkWorkMeta(userId);
		List<KeywordSimpleRes> keywordResList = chatService.callGptForTaste(tasteWorkMetaDto).tasteKeywords().stream()
			.map(gptRes -> new KeywordSimpleRes(gptRes.keyword(), gptRes.rank(), gptRes.percent()))
			.toList();
		tasteService.matchUserKeywords(userId, keywordResList);
	}
	public void updateNickname(Long userId, String nickname) {
		userService.updateNickname(userId, nickname);
	}
}
