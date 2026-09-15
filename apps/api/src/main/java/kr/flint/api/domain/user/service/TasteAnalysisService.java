package kr.flint.api.domain.user.service;

import java.util.List;

import org.springframework.stereotype.Service;

import kr.flint.infra.gpt.dto.GptKeywordDto;
import kr.flint.infra.gpt.dto.TasteWorkMetaDto;
import kr.flint.infra.gpt.service.ChatService;
import kr.flint.taste.dto.response.KeywordSimpleRes;
import kr.flint.taste.exception.TasteErrorCode;
import kr.flint.taste.exception.TasteExecption;
import kr.flint.taste.service.TasteService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TasteAnalysisService {

	private static final int MAX_ATTEMPTS = 2;

	private final ChatService chatService;
	private final TasteService tasteService;

	public void analyze(Long userId, List<TasteWorkMetaDto> works) {
		for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			try {
				tasteService.matchUserKeywords(userId, toKeywords(chatService.callGptForTaste(works)));
				return;
			} catch (TasteExecption exception) {
				if (exception.getErrorCode() != TasteErrorCode.INVALID_ANALYSIS || attempt == MAX_ATTEMPTS) {
					throw exception;
				}
			}
		}
	}

	private List<KeywordSimpleRes> toKeywords(GptKeywordDto result) {
		if (result == null || result.tasteKeywords() == null) {
			throw new TasteExecption(TasteErrorCode.INVALID_ANALYSIS);
		}
		return result.tasteKeywords().stream()
			.map(keyword -> new KeywordSimpleRes(keyword.keyword(), keyword.rank(), keyword.percent()))
			.toList();
	}
}
