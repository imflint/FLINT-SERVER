package kr.flint.api.domain.home.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import kr.flint.api.domain.home.dto.projection.CollectionBasicProjection;
import kr.flint.api.domain.home.port.CollectionRecommendationPort;
import kr.flint.api.domain.home.repository.HomeCollectionRepository;
import kr.flint.taste.domain.UserKeyword;
import kr.flint.taste.repository.UserKeywordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fliner-사용자 키워드 일치율 기반 추천 알고리즘
 * 1단계: Fliner-사용자 키워드 중복률로 상위 매칭 Fliner 선정
 * 2단계: 각 Fliner별 최신 컬렉션 최대 2개 선정
 * 3단계: maxSize 초과 시 조정 (Fliner별 1개씩 -> 남은 슬롯에 2번째 추가)
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "recommendation.strategy", havingValue = "fliner-keyword", matchIfMissing = true)
@RequiredArgsConstructor
public class FlinerKeywordRecommendation implements CollectionRecommendationPort {

	private final UserKeywordRepository userKeywordRepository;
	private final HomeCollectionRepository homeCollectionRepository;

	@Override
	public List<Long> recommend(Long userId, int maxSize) {
		log.info("[추천 알고리즘] userId={}, maxSize={}", userId, maxSize);
		long startTime = System.currentTimeMillis();

		List<Long> result = new ArrayList<>();

		// 사용자 키워드 조회
		Set<Long> userKeywordIds = new HashSet<>(userKeywordRepository.findKeywordIdsByUserId(userId));
		log.info("[1단계] 사용자 키워드 조회 완료. userId={}, keywordIds={}, count={}",
			userId, userKeywordIds, userKeywordIds.size());

		if (userKeywordIds.isEmpty()) {
			log.info("사용자 키워드 없음. Fallback으로 이동");
		} else {
			// Fliner 목록 조회
			List<Long> flinerIds = homeCollectionRepository.findAllFlinerIds();
			log.info("Fliner 목록 조회 완료. flinerCount={}", flinerIds.size());

			if (flinerIds.isEmpty()) {
				log.info("Fliner 없음. Fallback으로 이동");
			} else {
				// 3단계: Jaccard 유사도 계산
				List<FlinerMatch> flinerMatches = calculateFlinerMatches(flinerIds, userKeywordIds);
				log.info("Jaccard 유사도 계산 완료. matchedFlinerCount={}", flinerMatches.size());

				if (flinerMatches.isEmpty()) {
					log.info("유사한 Fliner 없음 (유사도 > 0). Fallback으로 이동");
				} else {
					// 상위 5명 Fliner 유사도 로깅
					flinerMatches.stream().limit(5).forEach(match ->
						log.debug("Fliner 유사도: flinerId={}, overlapRate={:.2f}",
							match.flinerId(), match.overlapRate())
					);

					// 4단계: 각 Fliner별 최신 컬렉션 할당
					assignLatestCollections(flinerMatches);
					int totalCollections = flinerMatches.stream()
						.mapToInt(m -> m.collections().size())
						.sum();
					log.info("Fliner별 컬렉션 할당 완료. totalCollections={}", totalCollections);

					// maxSize 조정
					result.addAll(adjustToLimit(flinerMatches, maxSize));
					log.info("maxSize 조정 완료. resultSize={}", result.size());
				}
			}
		}

		// Fallback (인기순)
		if (result.size() < maxSize) {
			int beforeSize = result.size();
			result = fillWithPopularCollections(result, maxSize);
			log.info("인기순 Fallback 적용. beforeSize={}, afterSize={}, addedCount={}",
				beforeSize, result.size(), result.size() - beforeSize);
		}

		long elapsed = System.currentTimeMillis() - startTime;
		log.info("[추천 완료] userId={}, resultSize={}, collectionIds={}, elapsed={}ms",
			userId, result.size(), result, elapsed);

		return result;
	}

	// 인기순 컬렉션으로 부족한 슬롯 채우기
	private List<Long> fillWithPopularCollections(List<Long> currentResult, int maxSize) {
		Set<Long> existingIds = new HashSet<>(currentResult);
		log.debug("인기순 Fallback 시작. currentSize={}, maxSize={}", currentResult.size(), maxSize);

		// 이미 추천된 것 제외를 위해 여유있게 조회
		List<Long> popularIds = homeCollectionRepository.findPopularPublicCollectionIds(maxSize + currentResult.size());
		log.debug("인기순 컬렉션 조회 완료. popularCount={}, popularIds={}", popularIds.size(), popularIds);

		List<Long> filled = new ArrayList<>(currentResult);
		List<Long> addedIds = new ArrayList<>();
		for (Long id : popularIds) {
			if (filled.size() >= maxSize) break;
			if (!existingIds.contains(id)) {
				filled.add(id);
				existingIds.add(id);
				addedIds.add(id);
			}
		}
		log.debug("인기순 Fallback 완료. addedCount={}, addedIds={}", addedIds.size(), addedIds);

		return filled;
	}

	// Fliner-사용자 키워드 중복률 계산 (Jaccard 유사도)
	private List<FlinerMatch> calculateFlinerMatches(List<Long> flinerIds, Set<Long> userKeywordIds) {
		List<UserKeyword> flinerKeywords = userKeywordRepository.findByUserIdIn(flinerIds);
		log.debug("Fliner 키워드 조회 완료. totalKeywords={}", flinerKeywords.size());

		Map<Long, Set<Long>> flinerKeywordMap = flinerKeywords.stream()
			.collect(Collectors.groupingBy(
				UserKeyword::getUserId,
				Collectors.mapping(UserKeyword::getKeywordId, Collectors.toSet())
			));
		log.debug("Fliner별 키워드 그룹화 완료. flinersWithKeywords={}", flinerKeywordMap.size());

		List<FlinerMatch> matches = flinerKeywordMap.entrySet().stream()
			.map(entry -> {
				Long flinerId = entry.getKey();
				Set<Long> flinerKeywordIds = entry.getValue();
				double overlapRate = calculateJaccardSimilarity(userKeywordIds, flinerKeywordIds);
				return new FlinerMatch(flinerId, overlapRate, new ArrayList<>());
			})
			.filter(match -> match.overlapRate() > 0)
			.sorted(Comparator.comparingDouble(FlinerMatch::overlapRate).reversed())
			.toList();

		log.debug("유사도 계산 완료. totalFliners={}, matchedFliners={}",
			flinerKeywordMap.size(), matches.size());

		return matches;
	}

	// 각 Fliner별 최신 컬렉션 할당 (최대 2개)
	private void assignLatestCollections(List<FlinerMatch> flinerMatches) {
		List<Long> flinerIds = flinerMatches.stream().map(FlinerMatch::flinerId).toList();
		log.debug("컬렉션 조회할 Fliner IDs: {}", flinerIds);

		List<CollectionBasicProjection> collections = homeCollectionRepository.findPublicCollectionsByFlinerIds(flinerIds);
		log.debug("Fliner 공개 컬렉션 조회 완료. totalCollections={}", collections.size());

		// Fliner별 컬렉션 그룹화
		Map<Long, List<CollectionBasicProjection>> flinerCollectionMap = collections.stream()
			.collect(Collectors.groupingBy(CollectionBasicProjection::getUserId));

		for (FlinerMatch flinerMatch : flinerMatches) {
			List<CollectionBasicProjection> flinerCollections = flinerCollectionMap.getOrDefault(
				flinerMatch.flinerId(), List.of()
			);

			// 최신순 정렬 후 최대 2개 선택
			List<CollectionInfo> collectionInfos = flinerCollections.stream()
				.sorted(Comparator.comparing(CollectionBasicProjection::getCreatedAt).reversed())
				.limit(2)
				.map(c -> new CollectionInfo(c.getId(), c.getCreatedAt()))
				.toList();

			flinerMatch.collections().addAll(collectionInfos);

			if (!collectionInfos.isEmpty()) {
				log.debug("Fliner 컬렉션 할당: flinerId={}, overlapRate={}, collectionIds={}",
					flinerMatch.flinerId(),
					String.format("%.2f", flinerMatch.overlapRate()),
					collectionInfos.stream().map(CollectionInfo::collectionId).toList());
			}
		}
	}

	// maxSize 제한 조정 로직
	private List<Long> adjustToLimit(List<FlinerMatch> flinerMatches, int maxSize) {
		List<Long> result = new ArrayList<>();

		// 1차: 각 Fliner별 1개씩 추가
		for (FlinerMatch flinerMatch : flinerMatches) {
			if (result.size() >= maxSize) break;
			if (!flinerMatch.collections().isEmpty()) {
				result.add(flinerMatch.collections().get(0).collectionId());
			}
		}
		log.debug("1차 할당 완료 (Fliner별 1개). resultSize={}, collectionIds={}",
			result.size(), result);

		// 2차: 남은 슬롯에 2번째 컬렉션 추가
		int beforeSecondPass = result.size();
		for (FlinerMatch flinerMatch : flinerMatches) {
			if (result.size() >= maxSize) break;
			if (flinerMatch.collections().size() > 1) {
				Long secondCollectionId = flinerMatch.collections().get(1).collectionId();
				if (!result.contains(secondCollectionId)) {
					result.add(secondCollectionId);
				}
			}
		}
		log.debug("2차 할당 완료 (2번째 컬렉션). addedCount={}, resultSize={}",
			result.size() - beforeSecondPass, result.size());

		return result;
	}

	// Jaccard 유사도 계산
	private double calculateJaccardSimilarity(Set<Long> set1, Set<Long> set2) {
		if (set1.isEmpty() || set2.isEmpty()) {
			return 0.0;
		}

		Set<Long> intersection = new HashSet<>(set1);
		intersection.retainAll(set2);

		Set<Long> union = new HashSet<>(set1);
		union.addAll(set2);

		return (double) intersection.size() / union.size();
	}

	record FlinerMatch(
		Long flinerId,
		double overlapRate,
		List<CollectionInfo> collections
	) {}

	record CollectionInfo(
		Long collectionId,
		LocalDateTime createdAt
	) {}
}
