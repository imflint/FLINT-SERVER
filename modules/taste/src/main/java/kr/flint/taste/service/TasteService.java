package kr.flint.taste.service;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import kr.flint.taste.domain.Keyword;
import kr.flint.taste.domain.KeywordLevel;
import kr.flint.taste.domain.UserKeyword;
import kr.flint.taste.dto.response.KeywordSimpleRes;
import kr.flint.taste.dto.response.UserKeywordProjection;
import kr.flint.taste.exception.TasteErrorCode;
import kr.flint.taste.exception.TasteExecption;
import kr.flint.taste.repository.CollectionKeywordRepository;
import kr.flint.taste.repository.KeywordRepository;
import kr.flint.taste.repository.UserKeywordRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TasteService {
	private static final int MAX_USER_KEYWORD_COUNT = 6;

    private final UserKeywordRepository userKeywordRepository;
	private final KeywordRepository keywordRepository;
	private final CollectionKeywordRepository collectionKeywordRepository;

	public List<UserKeywordProjection> getUserKeywords(Long userId) {
		List<UserKeywordProjection> projections = userKeywordRepository.findUserKeywordsWithDetails(userId).stream()
			.limit(MAX_USER_KEYWORD_COUNT)
			.toList();
		List<Integer> normalizedPercentages = normalizePercentages(
			projections.stream().map(UserKeywordProjection::getPercentage).toList()
		);
		return IntStream.range(0, projections.size())
			.mapToObj(index -> (UserKeywordProjection)NormalizedUserKeywordProjection.from(
				projections.get(index),
				index + 1,
				normalizedPercentages.get(index)
			))
			.toList();
    }

    public boolean hasUserKeywords(Long userId) {
        return userKeywordRepository.existsByUserId(userId);
    }


	//TODO : LV 대신 색상
	@Transactional
	public void matchUserKeywords(Long userId, List<KeywordSimpleRes> gptKeywordList){
		List<KeywordSimpleRes> normalizedKeywords = normalizeKeywords(gptKeywordList);
		List<String> keywordNameList = normalizedKeywords.stream()
			.map(KeywordSimpleRes::name)
			.toList();

		List<Keyword> keywordList = keywordRepository.findAllByNameIn(keywordNameList);

		Map<String, Keyword> keywordMap = keywordList.stream()
			.collect(Collectors.toMap(Keyword::getName, k -> k));


		if (keywordMap.size() != MAX_USER_KEYWORD_COUNT) {
			throw new TasteExecption(TasteErrorCode.INVALID_ANALYSIS);
		}

		List<Integer> normalizedPercentages = normalizePercentages(
			normalizedKeywords.stream().map(KeywordSimpleRes::percentage).toList()
		);

		List<UserKeyword> userKeywordList = IntStream.range(0, normalizedKeywords.size())
			.mapToObj(index -> {
				KeywordSimpleRes keywordRes = normalizedKeywords.get(index);
				Keyword keyword = keywordMap.get(keywordRes.name());
				return UserKeyword.create(
					userId,
					keyword.getId(),
					normalizedPercentages.get(index),
					index + 1
				);
			})
			.toList();

		userKeywordRepository.replaceAll(userId, userKeywordList);
	}

	private List<KeywordSimpleRes> normalizeKeywords(List<KeywordSimpleRes> keywords) {
		if (keywords == null || keywords.size() != MAX_USER_KEYWORD_COUNT) {
			throw new TasteExecption(TasteErrorCode.INVALID_ANALYSIS);
		}

		List<KeywordSimpleRes> normalized = keywords.stream()
			.map(keyword -> new KeywordSimpleRes(
				requireKeywordName(keyword),
				keyword.rank(),
				requireNonNegativePercentage(keyword)
			))
			.sorted(Comparator
				.comparingInt(KeywordSimpleRes::rank)
				.thenComparing(Comparator.comparingInt(KeywordSimpleRes::percentage).reversed())
				.thenComparing(KeywordSimpleRes::name))
			.toList();
		if (new HashSet<>(normalized.stream().map(KeywordSimpleRes::name).toList()).size()
			!= MAX_USER_KEYWORD_COUNT) {
			throw new TasteExecption(TasteErrorCode.INVALID_ANALYSIS);
		}
		return normalized;
	}

	private String requireKeywordName(KeywordSimpleRes keyword) {
		if (keyword == null || !StringUtils.hasText(keyword.name())) {
			throw new TasteExecption(TasteErrorCode.INVALID_ANALYSIS);
		}
		return keyword.name().trim();
	}

	private int requireNonNegativePercentage(KeywordSimpleRes keyword) {
		if (keyword.percentage() < 0) {
			throw new TasteExecption(TasteErrorCode.INVALID_ANALYSIS);
		}
		return keyword.percentage();
	}

	private List<Integer> normalizePercentages(List<Integer> percentages) {
		if (percentages.isEmpty()) {
			return List.of();
		}

		List<Integer> weights = percentages.stream()
			.map(value -> value == null ? 0 : Math.max(0, value))
			.toList();
		long total = weights.stream().mapToLong(Integer::longValue).sum();
		if (total == 0) {
			int base = 100 / weights.size();
			int remainder = 100 % weights.size();
			return IntStream.range(0, weights.size())
				.map(index -> base + (index < remainder ? 1 : 0))
				.boxed()
				.toList();
		}

		int[] normalized = new int[weights.size()];
		double[] remainders = new double[weights.size()];
		int assigned = 0;
		for (int index = 0; index < weights.size(); index++) {
			double exact = weights.get(index) * 100.0 / total;
			normalized[index] = (int)Math.floor(exact);
			remainders[index] = exact - normalized[index];
			assigned += normalized[index];
		}

		List<Integer> remainderOrder = IntStream.range(0, weights.size())
			.boxed()
			.sorted(Comparator
				.comparingDouble((Integer index) -> remainders[index]).reversed()
				.thenComparingInt(Integer::intValue))
			.toList();
		for (int index = 0; index < 100 - assigned; index++) {
			normalized[remainderOrder.get(index)]++;
		}
		return IntStream.of(normalized).boxed().toList();
	}

	private record NormalizedUserKeywordProjection(
		int ranking,
		String imageUrl,
		KeywordLevel level,
		String name,
		Integer percentage
	) implements UserKeywordProjection {
		private static NormalizedUserKeywordProjection from(
			UserKeywordProjection projection,
			int ranking,
			int percentage
		) {
			return new NormalizedUserKeywordProjection(
				ranking,
				projection.getImageUrl(),
				projection.getLevel(),
				projection.getName(),
				percentage
			);
		}

		@Override public int getRanking() { return ranking; }
		@Override public String getImageUrl() { return imageUrl; }
		@Override public KeywordLevel getLevel() { return level; }
		@Override public String getName() { return name; }
		@Override public Integer getPercentage() { return percentage; }
	}

	@Transactional
	public void deleteUserKeywords(final Long userId) {
		userKeywordRepository.deleteAllByUserId(userId);
	}
}
