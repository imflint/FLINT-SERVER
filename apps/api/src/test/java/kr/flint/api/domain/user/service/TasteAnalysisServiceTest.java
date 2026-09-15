package kr.flint.api.domain.user.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import kr.flint.infra.gpt.dto.GptKeywordDto;
import kr.flint.infra.gpt.dto.GptKeywordDto.TasteKeywords;
import kr.flint.infra.gpt.service.ChatService;
import kr.flint.taste.exception.TasteErrorCode;
import kr.flint.taste.exception.TasteExecption;
import kr.flint.taste.service.TasteService;

@ExtendWith(MockitoExtension.class)
class TasteAnalysisServiceTest {

	@Mock private ChatService chatService;
	@Mock private TasteService tasteService;

	@Test
	@DisplayName("첫 GPT 결과 검증 실패 시 한 번 재시도하고 성공 결과를 저장")
	void retriesInvalidAnalysisOnce() {
		TasteAnalysisService service = new TasteAnalysisService(chatService, tasteService);
		GptKeywordDto first = response("드라마");
		GptKeywordDto second = response("액션");
		when(chatService.callGptForTaste(List.of())).thenReturn(first, second);
		org.mockito.Mockito.doThrow(new TasteExecption(TasteErrorCode.INVALID_ANALYSIS))
			.doNothing()
			.when(tasteService).matchUserKeywords(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.anyList());

		service.analyze(1L, List.of());

		verify(chatService, times(2)).callGptForTaste(List.of());
		verify(tasteService, times(2)).matchUserKeywords(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.anyList());
	}

	@Test
	@DisplayName("두 번째 GPT 결과도 유효하지 않으면 오류를 반환")
	void failsAfterSecondInvalidAnalysis() {
		TasteAnalysisService service = new TasteAnalysisService(chatService, tasteService);
		when(chatService.callGptForTaste(List.of())).thenReturn(response("드라마"));
		org.mockito.Mockito.doThrow(new TasteExecption(TasteErrorCode.INVALID_ANALYSIS))
			.when(tasteService).matchUserKeywords(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.anyList());

		assertThatThrownBy(() -> service.analyze(1L, List.of()))
			.isInstanceOf(TasteExecption.class);
		verify(chatService, times(2)).callGptForTaste(List.of());
	}

	private GptKeywordDto response(String keyword) {
		return new GptKeywordDto(List.of(new TasteKeywords(1, "LV1", "Genre", keyword, 100)));
	}
}
