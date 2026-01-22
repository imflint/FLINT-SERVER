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
		List<Long> result = new ArrayList<>();

		// 사용자 키워드 기반 추천 시도
		Set<Long> userKeywordIds = new HashSet<>(userKeywordRepository.findKeywordIdsByUserId(userId));
		if (!userKeywordIds.isEmpty()) {
			List<Long> flinerIds = homeCollectionRepository.findAllFlinerIds();
			if (!flinerIds.isEmpty()) {
				List<FlinerMatch> flinerMatches = calculateFlinerMatches(flinerIds, userKeywordIds);
				if (!flinerMatches.isEmpty()) {
					assignLatestCollections(flinerMatches);
					result.addAll(adjustToLimit(flinerMatches, maxSize));
				}
			}
		}

		// Fallback: 추천 결과가 maxSize보다 적으면 인기순으로 채우기
		if (result.size() < maxSize) {
			result = fillWithPopularCollections(result, maxSize);
			log.debug("인기순 Fallback 적용. userId={}, resultSize={}", userId, result.size());
		}

		return result;
	}

	// 인기순 컬렉션으로 부족한 슬롯 채우기
	private List<Long> fillWithPopularCollections(List<Long> currentResult, int maxSize) {
		Set<Long> existingIds = new HashSet<>(currentResult);

		// 이미 추천된 것 제외를 위해 여유있게 조회
		List<Long> popularIds = homeCollectionRepository.findPopularPublicCollectionIds(maxSize + currentResult.size());

		List<Long> filled = new ArrayList<>(currentResult);
		for (Long id : popularIds) {
			if (filled.size() >= maxSize) break;
			if (!existingIds.contains(id)) {
				filled.add(id);
				existingIds.add(id);
			}
		}

		return filled;
	}

	// Fliner-사용자 키워드 중복률 계산 (Jaccard 유사도)
	private List<FlinerMatch> calculateFlinerMatches(List<Long> flinerIds, Set<Long> userKeywordIds) {
		List<UserKeyword> flinerKeywords = userKeywordRepository.findByUserIdIn(flinerIds);

		Map<Long, Set<Long>> flinerKeywordMap = flinerKeywords.stream()
			.collect(Collectors.groupingBy(
				UserKeyword::getUserId,
				Collectors.mapping(UserKeyword::getKeywordId, Collectors.toSet())
			));

		return flinerKeywordMap.entrySet().stream()
			.map(entry -> {
				Long flinerId = entry.getKey();
				Set<Long> flinerKeywordIds = entry.getValue();
				double overlapRate = calculateJaccardSimilarity(userKeywordIds, flinerKeywordIds);
				return new FlinerMatch(flinerId, overlapRate, new ArrayList<>());
			})
			.filter(match -> match.overlapRate() > 0)
			.sorted(Comparator.comparingDouble(FlinerMatch::overlapRate).reversed())
			.toList();
	}

	// 각 Fliner별 최신 컬렉션 할당 (최대 2개)
	private void assignLatestCollections(List<FlinerMatch> flinerMatches) {
		List<Long> flinerIds = flinerMatches.stream().map(FlinerMatch::flinerId).toList();

		List<CollectionBasicProjection> collections = homeCollectionRepository.findPublicCollectionsByFlinerIds(flinerIds);

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

		// 2차: 남은 슬롯에 2번째 컬렉션 추가
		for (FlinerMatch flinerMatch : flinerMatches) {
			if (result.size() >= maxSize) break;
			if (flinerMatch.collections().size() > 1) {
				Long secondCollectionId = flinerMatch.collections().get(1).collectionId();
				if (!result.contains(secondCollectionId)) {
					result.add(secondCollectionId);
				}
			}
		}

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
