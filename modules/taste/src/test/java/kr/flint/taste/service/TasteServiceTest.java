package kr.flint.taste.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import kr.flint.taste.exception.TasteExecption;
import kr.flint.taste.dto.response.UserKeywordProjection;

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
	@DisplayName("GPT 순위가 동점이어도 정확히 6개를 1부터 6까지 고유 순위로 교체")
	void matchUserKeywordsNormalizesDuplicateRanks() {
		Long userId = 1L;
		List<KeywordSimpleRes> gptKeywords = List.of(
			new KeywordSimpleRes("드라마", 1, 90),
			new KeywordSimpleRes("모험", 2, 80),
			new KeywordSimpleRes("범죄", 2, 70),
			new KeywordSimpleRes("액션", 3, 60),
			new KeywordSimpleRes("로맨스", 4, 50),
			new KeywordSimpleRes("코미디", 5, 40)
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
		assertThat(captor.getValue())
			.extracting(UserKeyword::getPercentage)
			.satisfies(percentages -> assertThat(percentages.stream().mapToInt(Integer::intValue).sum()).isEqualTo(100));
	}

	@Test
	@DisplayName("가중치가 모두 0이면 17, 17, 17, 17, 16, 16으로 저장")
	void matchUserKeywordsDistributesZeroWeights() {
		Long userId = 1L;
		List<KeywordSimpleRes> gptKeywords = List.of(
			new KeywordSimpleRes("드라마", 1, 0),
			new KeywordSimpleRes("모험", 2, 0),
			new KeywordSimpleRes("범죄", 3, 0),
			new KeywordSimpleRes("액션", 4, 0),
			new KeywordSimpleRes("로맨스", 5, 0),
			new KeywordSimpleRes("코미디", 6, 0)
		);
		when(keywordRepository.findAllByNameIn(anyList())).thenReturn(IntStream.range(0, 6)
			.mapToObj(index -> keyword(100L + index, gptKeywords.get(index).name()))
			.toList());

		tasteService.matchUserKeywords(userId, gptKeywords);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<UserKeyword>> captor = ArgumentCaptor.forClass(List.class);
		verify(userKeywordRepository).replaceAll(org.mockito.ArgumentMatchers.eq(userId), captor.capture());
		assertThat(captor.getValue())
			.extracting(UserKeyword::getPercentage)
			.containsExactly(17, 17, 17, 17, 16, 16);
	}

	@Test
	@DisplayName("중복 키워드 분석 결과는 기존 데이터를 교체하지 않고 실패")
	void matchUserKeywordsRejectsDuplicates() {
		List<KeywordSimpleRes> duplicated = List.of(
			new KeywordSimpleRes("드라마", 1, 20),
			new KeywordSimpleRes("드라마", 2, 20),
			new KeywordSimpleRes("범죄", 3, 20),
			new KeywordSimpleRes("액션", 4, 20),
			new KeywordSimpleRes("로맨스", 5, 10),
			new KeywordSimpleRes("코미디", 6, 10)
		);

		assertThatThrownBy(() -> tasteService.matchUserKeywords(1L, duplicated))
			.isInstanceOf(TasteExecption.class);
		verify(userKeywordRepository, never()).replaceAll(org.mockito.ArgumentMatchers.anyLong(), anyList());
	}

	@Test
	@DisplayName("기존 키워드 조회도 비율 합계를 100으로 정규화")
	void getUserKeywordsNormalizesStoredPercentages() {
		List<UserKeywordProjection> projections = IntStream.rangeClosed(1, 6)
			.mapToObj(index -> projection(index, 10))
			.toList();
		when(userKeywordRepository.findUserKeywordsWithDetails(1L)).thenReturn(projections);

		List<UserKeywordProjection> result = tasteService.getUserKeywords(1L);

		assertThat(result).extracting(UserKeywordProjection::getRanking)
			.containsExactly(1, 2, 3, 4, 5, 6);
		assertThat(result).extracting(UserKeywordProjection::getPercentage)
			.containsExactly(17, 17, 17, 17, 16, 16);
	}

	private Keyword keyword(Long id, String name) {
		Keyword keyword = Keyword.create(name, KeywordLevel.LV1);
		ReflectionTestUtils.setField(keyword, "id", id);
		return keyword;
	}

	private UserKeywordProjection projection(int rank, int percentage) {
		UserKeywordProjection projection = mock(UserKeywordProjection.class);
		when(projection.getName()).thenReturn("키워드 " + rank);
		when(projection.getLevel()).thenReturn(KeywordLevel.LV1);
		when(projection.getImageUrl()).thenReturn("image-" + rank + ".png");
		when(projection.getPercentage()).thenReturn(percentage);
		return projection;
	}
}
