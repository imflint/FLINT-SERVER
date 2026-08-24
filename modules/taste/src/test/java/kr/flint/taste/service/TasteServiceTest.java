package kr.flint.taste.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import kr.flint.taste.domain.Keyword;
import kr.flint.taste.domain.KeywordLevel;
import kr.flint.taste.domain.UserKeyword;
import kr.flint.taste.dto.response.KeywordSimpleRes;
import kr.flint.taste.repository.CollectionKeywordRepository;
import kr.flint.taste.repository.KeywordRepository;
import kr.flint.taste.repository.UserKeywordRepository;

@ExtendWith(MockitoExtension.class)
class TasteServiceTest {

	@Mock
	private UserKeywordRepository userKeywordRepository;

	@Mock
	private KeywordRepository keywordRepository;

	@Mock
	private CollectionKeywordRepository collectionKeywordRepository;

	@InjectMocks
	private TasteService tasteService;

	@Test
	@DisplayName("GPT 순위가 동점이어도 최대 6개를 1부터 6까지 고유 순위로 교체")
	void matchUserKeywordsNormalizesDuplicateRanks() {
		Long userId = 1L;
		List<KeywordSimpleRes> gptKeywords = List.of(
			new KeywordSimpleRes("드라마", 1, 90),
			new KeywordSimpleRes("모험", 2, 80),
			new KeywordSimpleRes("범죄", 2, 70),
			new KeywordSimpleRes("액션", 3, 60),
			new KeywordSimpleRes("로맨스", 4, 50),
			new KeywordSimpleRes("코미디", 5, 40),
			new KeywordSimpleRes("호러", 6, 30)
		);
		List<Keyword> keywords = IntStream.range(0, gptKeywords.size())
			.mapToObj(index -> keyword(100L + index, gptKeywords.get(index).name()))
			.toList();
		when(keywordRepository.findAllByNameIn(anyList())).thenReturn(keywords);

		tasteService.matchUserKeywords(userId, gptKeywords);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<UserKeyword>> captor = ArgumentCaptor.forClass(List.class);
		verify(userKeywordRepository).replaceAll(org.mockito.ArgumentMatchers.eq(userId), captor.capture());
		assertThat(captor.getValue()).hasSize(6);
		assertThat(captor.getValue())
			.extracting(UserKeyword::getRanking)
			.containsExactly(1, 2, 3, 4, 5, 6);
		assertThat(captor.getValue())
			.extracting(UserKeyword::getKeywordId)
			.containsExactly(100L, 101L, 102L, 103L, 104L, 105L);
	}

	private Keyword keyword(Long id, String name) {
		Keyword keyword = Keyword.create(name, KeywordLevel.LV1);
		ReflectionTestUtils.setField(keyword, "id", id);
		return keyword;
	}
}
