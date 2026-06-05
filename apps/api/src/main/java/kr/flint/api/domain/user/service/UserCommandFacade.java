package kr.flint.api.domain.user.service;

import java.util.List;

import kr.flint.api.domain.bookmark.repository.BookmarkQueryRepository;
import kr.flint.infra.gpt.dto.TasteWorkMetaDto;
import kr.flint.infra.gpt.service.ChatService;
import kr.flint.taste.dto.response.KeywordSimpleRes;
import kr.flint.taste.service.TasteService;
import kr.flint.user.domain.User;
import kr.flint.user.exception.UserErrorCode;
import kr.flint.user.exception.UserException;
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

	// 사용자가 명시적으로 재계산 버튼을 눌렀을 때 호출 — 임계값 미달이면 거부, 통과 시 callGpt 후 카운터 리셋
	public void recalculateKeywordOnDemand(Long userId) {
		User user = userService.getById(userId);
		if (!user.isKeywordRecalculatable()) {
			throw new UserException(UserErrorCode.KEYWORD_RECALC_NOT_READY);
		}
		callGpt(userId);
		user.resetBookmarksSinceKeywordCalc();
	}

	public void updateProfileImage(Long userId, String profileImage) {
		userService.updateProfileImage(userId, profileImage);
	}

	public void updateNickname(Long userId, String nickname) {
		userService.updateNickname(userId, nickname);
	}
}
