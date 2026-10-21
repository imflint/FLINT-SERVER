package kr.flint.taste.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import kr.flint.taste.domain.Keyword;
import kr.flint.taste.domain.KeywordCatalog;
import kr.flint.taste.domain.KeywordLevel;
import kr.flint.taste.domain.UserKeyword;
import kr.flint.taste.dto.KeywordCatalogSyncResult;
import kr.flint.taste.dto.response.KeywordSimpleRes;
import kr.flint.taste.dto.response.UserKeywordProjection;
import kr.flint.taste.exception.TasteErrorCode;
import kr.flint.taste.exception.TasteExecption;
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
	/** 상위 CORE 구간. 이 구간은 서로 다른 레벨에서 뽑아 색이 겹치지 않게 한다. */
	private static final int CORE_KEYWORD_COUNT = 3;
	/** 같은 레벨(= 같은 색)이 6개 안에서 최대 몇 번까지 나올 수 있는지 */
	private static final int MAX_KEYWORD_COUNT_PER_LEVEL = 2;

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

	/**
	 * GPT 후보 키워드를 사용자 취향 키워드 6개로 확정한다.
	 *
	 * <p>이름은 {@link KeywordCatalog}로 정식 표기로 되돌린 뒤 DB와 맞추고, 상위 3개는 서로
	 * 다른 레벨에서 고른다. 색은 레벨에서 파생되므로 CORE 구간의 색 중복이 선택 단계에서 사라진다.
	 */
	@Transactional
	public void matchUserKeywords(Long userId, List<KeywordSimpleRes> gptKeywordList){
		List<KeywordSimpleRes> candidates = toCanonicalCandidates(gptKeywordList);

		Map<String, Keyword> keywordMap = keywordRepository
			.findAllByNameIn(candidates.stream().map(KeywordSimpleRes::name).toList())
			.stream()
			.collect(Collectors.toMap(Keyword::getName, keyword -> keyword, (first, ignored) -> first));

		List<KeywordSimpleRes> resolved = candidates.stream()
			.filter(candidate -> {
				boolean exists = keywordMap.containsKey(candidate.name());
				if (!exists) {
					log.warn("카탈로그에는 있지만 DB에 없는 키워드 무시: {}", candidate.name());
				}
				return exists;
			})
			.toList();

		if (resolved.size() < MAX_USER_KEYWORD_COUNT) {
			log.warn("유효 키워드 부족으로 취향 분석 실패. userId={}, resolved={}", userId, resolved.size());
			throw new TasteExecption(TasteErrorCode.INVALID_ANALYSIS);
		}

		List<KeywordSimpleRes> selected = selectWithLevelDiversity(resolved);
		List<Integer> normalizedPercentages = normalizePercentages(
			selected.stream().map(KeywordSimpleRes::percentage).toList()
		);

		List<UserKeyword> userKeywordList = IntStream.range(0, selected.size())
			.mapToObj(index -> UserKeyword.create(
				userId,
				keywordMap.get(selected.get(index).name()).getId(),
				normalizedPercentages.get(index),
				index + 1
			))
			.toList();

		userKeywordRepository.replaceAll(userId, userKeywordList);
	}

	/**
	 * DB {@code keywords} 테이블을 카탈로그에 맞춘다. 없는 키워드는 추가하고, level 이 어긋난
	 * 행은 카탈로그 값으로 보정한다. 카탈로그에 없는 기존 행은 지우지 않고 보고만 한다.
	 */
	@Transactional
	public KeywordCatalogSyncResult syncKeywordCatalog() {
		Map<String, Keyword> existing = keywordRepository.findAll().stream()
			.collect(Collectors.toMap(Keyword::getName, keyword -> keyword, (first, ignored) -> first));

		List<Keyword> dirty = new ArrayList<>();
		int created = 0;
		int levelCorrected = 0;
		for (KeywordCatalog.Entry entry : KeywordCatalog.entries()) {
			Keyword keyword = existing.get(entry.name());
			if (keyword == null) {
				dirty.add(Keyword.create(entry.name(), entry.level()));
				created++;
				continue;
			}
			if (!keyword.hasLevel(entry.level())) {
				log.warn("키워드 레벨 보정: name={}, {} -> {}", entry.name(), keyword.getLevel(), entry.level());
				keyword.changeLevel(entry.level());
				dirty.add(keyword);
				levelCorrected++;
			}
		}
		if (!dirty.isEmpty()) {
			keywordRepository.saveAll(dirty);
		}

		int unknown = (int)existing.keySet().stream()
			.filter(name -> KeywordCatalog.findEntry(name).isEmpty())
			.peek(name -> log.warn("카탈로그에 없는 DB 키워드(미사용 가능성): {}", name))
			.count();

		return new KeywordCatalogSyncResult(created, levelCorrected, unknown);
	}

	// 표기 변형을 정식 이름으로 되돌리고, 순위 순서로 정렬해 중복을 제거한다
	private List<KeywordSimpleRes> toCanonicalCandidates(List<KeywordSimpleRes> keywords) {
		if (keywords == null || keywords.isEmpty()) {
			throw new TasteExecption(TasteErrorCode.INVALID_ANALYSIS);
		}

		Map<String, KeywordSimpleRes> deduped = keywords.stream()
			.map(this::toCanonical)
			.filter(Objects::nonNull)
			.sorted(Comparator
				.comparingInt(KeywordSimpleRes::rank)
				.thenComparing(Comparator.comparingInt(KeywordSimpleRes::percentage).reversed())
				.thenComparing(KeywordSimpleRes::name))
			.collect(Collectors.toMap(
				KeywordSimpleRes::name,
				keyword -> keyword,
				(first, ignored) -> first,
				LinkedHashMap::new
			));
		return List.copyOf(deduped.values());
	}

	private KeywordSimpleRes toCanonical(KeywordSimpleRes keyword) {
		if (keyword == null || !StringUtils.hasText(keyword.name()) || keyword.percentage() < 0) {
			log.warn("형식이 잘못된 GPT 키워드 무시: {}", keyword);
			return null;
		}
		return KeywordCatalog.canonicalize(keyword.name())
			.map(canonicalName -> new KeywordSimpleRes(canonicalName, keyword.rank(), keyword.percentage()))
			.orElseGet(() -> {
				log.warn("카탈로그에 없는 GPT 키워드 무시: {}", keyword.name());
				return null;
			});
	}

	/**
	 * 순위 순서로 훑으면서 CORE 3개는 서로 다른 레벨에서, 나머지는 레벨당 최대
	 * {@value #MAX_KEYWORD_COUNT_PER_LEVEL}개까지 고른다. 한 방향으로만 훑기 때문에
	 * 선택된 6개의 상대 순서(= 비율 내림차순)는 유지된다.
	 */
	private List<KeywordSimpleRes> selectWithLevelDiversity(List<KeywordSimpleRes> candidates) {
		List<KeywordSimpleRes> selected = new ArrayList<>(MAX_USER_KEYWORD_COUNT);
		List<KeywordSimpleRes> skipped = new ArrayList<>();
		Set<KeywordLevel> coreLevels = EnumSet.noneOf(KeywordLevel.class);
		Map<KeywordLevel, Integer> levelCounts = new EnumMap<>(KeywordLevel.class);

		for (KeywordSimpleRes candidate : candidates) {
			if (selected.size() == MAX_USER_KEYWORD_COUNT) {
				break;
			}
			KeywordLevel level = KeywordCatalog.levelOf(candidate.name(), null);
			boolean core = selected.size() < CORE_KEYWORD_COUNT;
			boolean acceptable = core
				? !coreLevels.contains(level)
				: levelCounts.getOrDefault(level, 0) < MAX_KEYWORD_COUNT_PER_LEVEL;
			if (!acceptable) {
				skipped.add(candidate);
				continue;
			}
			selected.add(candidate);
			if (core) {
				coreLevels.add(level);
			}
			levelCounts.merge(level, 1, Integer::sum);
		}

		// 후보 레벨이 한쪽으로 쏠려 6개를 못 채우면 순위 순서대로 채운다(색 중복 허용)
		for (KeywordSimpleRes candidate : skipped) {
			if (selected.size() == MAX_USER_KEYWORD_COUNT) {
				break;
			}
			selected.add(candidate);
		}

		if (selected.size() != MAX_USER_KEYWORD_COUNT) {
			throw new TasteExecption(TasteErrorCode.INVALID_ANALYSIS);
		}
		return List.copyOf(selected);
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
