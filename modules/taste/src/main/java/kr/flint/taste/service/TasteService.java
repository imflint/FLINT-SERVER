package kr.flint.taste.service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import kr.flint.taste.domain.Keyword;
import kr.flint.taste.domain.UserKeyword;
import kr.flint.taste.dto.response.KeywordSimpleRes;
import kr.flint.taste.dto.response.UserKeywordProjection;
import kr.flint.taste.repository.CollectionKeywordRepository;
import kr.flint.taste.repository.KeywordRepository;
import kr.flint.taste.repository.UserKeywordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class TasteService {
	private static final int MAX_USER_KEYWORD_COUNT = 6;

    private final UserKeywordRepository userKeywordRepository;
	private final KeywordRepository keywordRepository;
	private final CollectionKeywordRepository collectionKeywordRepository;

	public List<UserKeywordProjection> getUserKeywords(Long userId) {
        return userKeywordRepository.findUserKeywordsWithDetails(userId);
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


		List<KeywordSimpleRes> validKeywords = normalizedKeywords.stream()
				.filter(keywordRes -> {
					boolean exists = keywordMap.containsKey(keywordRes.name());
					if (!exists) {
						log.warn("DB에 없는 키워드 무시: {}", keywordRes.name());
					}
					return exists;
				})
				.limit(MAX_USER_KEYWORD_COUNT)
				.toList();

		List<UserKeyword> userKeywordList = IntStream.range(0, validKeywords.size())
			.mapToObj(index -> {
				KeywordSimpleRes keywordRes = validKeywords.get(index);
				Keyword keyword = keywordMap.get(keywordRes.name());
				return UserKeyword.create(
					userId,
					keyword.getId(),
					keywordRes.percentage(),
					index + 1
				);
			})
			.toList();

		userKeywordRepository.replaceAll(userId, userKeywordList);
	}

	private List<KeywordSimpleRes> normalizeKeywords(List<KeywordSimpleRes> keywords) {
		if (keywords == null || keywords.isEmpty()) {
			return List.of();
		}

		return keywords.stream()
			.filter(keyword -> keyword != null && StringUtils.hasText(keyword.name()))
			.map(keyword -> new KeywordSimpleRes(
				keyword.name().trim(),
				keyword.rank(),
				keyword.percentage()
			))
			.sorted(Comparator
				.comparingInt(KeywordSimpleRes::rank)
				.thenComparing(Comparator.comparingInt(KeywordSimpleRes::percentage).reversed())
				.thenComparing(KeywordSimpleRes::name))
			.collect(Collectors.collectingAndThen(
				Collectors.toMap(
					KeywordSimpleRes::name,
					keyword -> keyword,
					(first, ignored) -> first,
					LinkedHashMap::new
				),
				map -> List.copyOf(map.values())
			));
	}

	@Transactional
	public void deleteUserKeywords(final Long userId) {
		userKeywordRepository.deleteAllByUserId(userId);
	}
}
