package kr.flint.taste.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
import kr.flint.taste.domain.KeywordCatalog;
import kr.flint.taste.domain.KeywordColor;
import kr.flint.taste.domain.KeywordLevel;
import kr.flint.taste.domain.UserKeyword;
import kr.flint.taste.dto.KeywordCatalogSyncResult;
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

	@Test
	@DisplayName("별칭/공백이 흔들린 GPT 키워드도 정식 이름으로 복구해 DB와 맞춘다")
	void matchUserKeywordsCanonicalizesDriftedNames() {
		List<KeywordSimpleRes> gptKeywords = List.of(
			new KeywordSimpleRes("공포", 1, 30),
			new KeywordSimpleRes("미국", 2, 25),
			new KeywordSimpleRes(" 드라마 ", 3, 20),
			new KeywordSimpleRes("애니", 4, 10),
			new KeywordSimpleRes("노년", 5, 10),
			new KeywordSimpleRes("사극", 6, 5)
		);
		List<String> canonicalNames = List.of("호러", "북미", "드라마", "애니메이션", "노년의 삶", "사극");
		when(keywordRepository.findAllByNameIn(anyList())).thenReturn(IntStream.range(0, canonicalNames.size())
			.mapToObj(index -> keyword(200L + index, canonicalNames.get(index)))
			.toList());

		tasteService.matchUserKeywords(1L, gptKeywords);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<String>> nameCaptor = ArgumentCaptor.forClass(List.class);
		verify(keywordRepository).findAllByNameIn(nameCaptor.capture());
		assertThat(nameCaptor.getValue()).containsExactlyInAnyOrderElementsOf(canonicalNames);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<UserKeyword>> captor = ArgumentCaptor.forClass(List.class);
		verify(userKeywordRepository).replaceAll(org.mockito.ArgumentMatchers.eq(1L), captor.capture());
		assertThat(captor.getValue()).hasSize(6);
	}

	@Test
	@DisplayName("상위 3개는 서로 다른 레벨에서 골라 CORE 색상이 겹치지 않는다")
	void matchUserKeywordsSpreadsCoreLevels() {
		List<String> candidateNames = List.of(
			"드라마", "액션", "범죄", "복수", "한국", "어두운", "성장", "사극", "코미디", "우정", "일본", "슬픈"
		);
		List<KeywordSimpleRes> gptKeywords = IntStream.range(0, candidateNames.size())
			.mapToObj(index -> new KeywordSimpleRes(candidateNames.get(index), index + 1, 50 - index * 3))
			.toList();
		Map<String, Long> idsByName = new java.util.HashMap<>();
		when(keywordRepository.findAllByNameIn(anyList())).thenReturn(IntStream.range(0, candidateNames.size())
			.mapToObj(index -> {
				idsByName.put(candidateNames.get(index), 300L + index);
				return keyword(300L + index, candidateNames.get(index));
			})
			.toList());

		tasteService.matchUserKeywords(1L, gptKeywords);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<UserKeyword>> captor = ArgumentCaptor.forClass(List.class);
		verify(userKeywordRepository).replaceAll(org.mockito.ArgumentMatchers.eq(1L), captor.capture());
		List<UserKeyword> saved = captor.getValue();
		assertThat(saved).hasSize(6);
		assertThat(saved).extracting(UserKeyword::getRanking).containsExactly(1, 2, 3, 4, 5, 6);
		// 순위 1~3 은 LV1/LV3/LV5 로 분산되고, 같은 레벨이던 액션·범죄는 밀려난다
		assertThat(saved).extracting(UserKeyword::getKeywordId)
			.containsExactly(
				idsByName.get("드라마"),
				idsByName.get("복수"),
				idsByName.get("한국"),
				idsByName.get("어두운"),
				idsByName.get("성장"),
				idsByName.get("사극")
			);

		List<KeywordColor> coreColors = List.of("드라마", "복수", "한국").stream()
			.map(name -> KeywordCatalog.colorOf(name, null))
			.toList();
		assertThat(coreColors).doesNotHaveDuplicates();
		// 비율은 순위를 따라 단조 감소한다
		assertThat(saved).extracting(UserKeyword::getPercentage)
			.isSortedAccordingTo(Comparator.reverseOrder());
	}

	@Test
	@DisplayName("같은 레벨만 후보로 오면 색 중복을 허용하고 순위 순서를 유지한다")
	void matchUserKeywordsKeepsRankOrderWhenAllCandidatesShareLevel() {
		List<String> sameLevelNames = List.of("드라마", "모험", "범죄", "액션", "로맨스", "코미디");
		List<KeywordSimpleRes> gptKeywords = IntStream.range(0, sameLevelNames.size())
			.mapToObj(index -> new KeywordSimpleRes(sameLevelNames.get(index), index + 1, 60 - index * 5))
			.toList();
		when(keywordRepository.findAllByNameIn(anyList())).thenReturn(IntStream.range(0, sameLevelNames.size())
			.mapToObj(index -> keyword(400L + index, sameLevelNames.get(index)))
			.toList());

		tasteService.matchUserKeywords(1L, gptKeywords);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<UserKeyword>> captor = ArgumentCaptor.forClass(List.class);
		verify(userKeywordRepository).replaceAll(org.mockito.ArgumentMatchers.eq(1L), captor.capture());
		assertThat(captor.getValue()).extracting(UserKeyword::getKeywordId)
			.containsExactly(400L, 401L, 402L, 403L, 404L, 405L);
	}

	@Test
	@DisplayName("카탈로그에 없는 키워드만 오면 기존 데이터를 교체하지 않고 실패")
	void matchUserKeywordsRejectsUnknownKeywords() {
		List<KeywordSimpleRes> unknown = IntStream.rangeClosed(1, 6)
			.mapToObj(rank -> new KeywordSimpleRes("없는키워드" + rank, rank, 20))
			.toList();

		assertThatThrownBy(() -> tasteService.matchUserKeywords(1L, unknown))
			.isInstanceOf(TasteExecption.class);
		verify(userKeywordRepository, never()).replaceAll(org.mockito.ArgumentMatchers.anyLong(), anyList());
	}

	@Test
	@DisplayName("카탈로그 동기화는 누락 키워드를 추가하고 어긋난 레벨을 보정한다")
	void syncKeywordCatalogCreatesMissingAndCorrectsLevel() {
		Keyword misleveled = keyword(500L, "한국");
		Keyword correct = keyword(501L, "드라마");
		when(keywordRepository.findAll()).thenReturn(List.of(misleveled, correct));

		KeywordCatalogSyncResult result = tasteService.syncKeywordCatalog();

		assertThat(result.created()).isEqualTo(KeywordCatalog.size() - 2);
		assertThat(result.levelCorrected()).isEqualTo(1);
		assertThat(result.unknown()).isZero();
		assertThat(misleveled.getLevel()).isEqualTo(KeywordLevel.LV5);
		assertThat(correct.getLevel()).isEqualTo(KeywordLevel.LV1);
		verify(keywordRepository).saveAll(anyList());
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
