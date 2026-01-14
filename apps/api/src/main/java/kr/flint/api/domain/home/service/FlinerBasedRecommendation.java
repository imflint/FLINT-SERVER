package kr.flint.api.domain.home.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import kr.flint.api.domain.home.dto.projection.CollectionBasicProjection;
import kr.flint.api.domain.home.port.CollectionRecommendationPort;
import kr.flint.api.domain.home.repository.HomeCollectionRepository;
import kr.flint.collection.domain.CollectionKeyword;
import kr.flint.collection.repository.CollectionKeywordRepository;
import kr.flint.taste.domain.UserKeyword;
import kr.flint.taste.repository.UserKeywordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 추천 알고리즘:
 * 1단계: Fliner-사용자 키워드 중복률로 상위 매칭 Fliner 선정
 * 2단계: 각 Fliner별 컬렉션-사용자 키워드 중복률 높은 순 최대 2개 선정
 * 3단계: 10개 초과 시 조정 (Fliner별 1개씩 -> 남은 슬롯에 2번째 추가)
 */
@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class FlinerBasedRecommendation implements CollectionRecommendationPort {

    private final UserKeywordRepository userKeywordRepository;
    private final CollectionKeywordRepository collectionKeywordRepository;
    private final HomeCollectionRepository homeCollectionRepository;

    @Override
    public List<Long> recommend(Long userId, int maxSize) {
        // 사용자 키워드 ID 목록 조회
        Set<Long> userKeywordIds = new HashSet<>(userKeywordRepository.findKeywordIdsByUserId(userId));
        if (userKeywordIds.isEmpty()) {
            log.debug("사용자 키워드가 없습니다. userId={}", userId);
            return List.of();
        }

        // 모든 Fliner 조회
        List<Long> flinerIds = homeCollectionRepository.findAllFlinerIds();
        if (flinerIds.isEmpty()) {
            log.debug("Fliner가 없습니다.");
            return List.of();
        }

        // Fliner-사용자 키워드 중복률 계산 및 정렬
        List<FlinerMatch> flinerMatches = calculateFlinerMatches(flinerIds, userKeywordIds);
        if (flinerMatches.isEmpty()) {
            return List.of();
        }

        // 각 Fliner별 컬렉션-사용자 키워드 중복률 계산
        enrichWithCollectionMatches(flinerMatches, userKeywordIds);

        // 10개 제한 조정
        return adjustToLimit(flinerMatches, maxSize);
    }

    // Fliner-사용자 키워드 중복률 계산
    private List<FlinerMatch> calculateFlinerMatches(List<Long> flinerIds, Set<Long> userKeywordIds) {
        // Fliner들의 키워드 조회
        List<UserKeyword> flinerKeywords = userKeywordRepository.findByUserIdIn(flinerIds);

        // Fliner별 키워드 그룹화
        Map<Long, Set<Long>> flinerKeywordMap = flinerKeywords.stream()
            .collect(Collectors.groupingBy(
                UserKeyword::getUserId,
                Collectors.mapping(UserKeyword::getKeywordId, Collectors.toSet())
            ));

        // Fliner-사용자 중복률 계산 및 정렬
        return flinerKeywordMap.entrySet().stream()
            .map(entry -> {
                Long flinerId = entry.getKey();
                Set<Long> flinerKeywordIds = entry.getValue();
                double overlapRate = calculateOverlapRate(userKeywordIds, flinerKeywordIds);
                return new FlinerMatch(flinerId, overlapRate, new ArrayList<>());
            })
            .filter(match -> match.overlapRate() > 0)
            .sorted(Comparator.comparingDouble(FlinerMatch::overlapRate).reversed())
            .toList();
    }

    // 각 Fliner별 컬렉션-사용자 키워드 중복률 계산
    private void enrichWithCollectionMatches(List<FlinerMatch> flinerMatches, Set<Long> userKeywordIds) {
        List<Long> flinerIds = flinerMatches.stream().map(FlinerMatch::flinerId).toList();

        // Fliner들의 공개 컬렉션 조회
        List<CollectionBasicProjection> collections = homeCollectionRepository.findPublicCollectionsByFlinerIds(flinerIds);

        // 컬렉션 ID 목록
        List<Long> collectionIds = collections.stream().map(CollectionBasicProjection::getId).toList();

        // 컬렉션별 키워드 조회 (배치)
        List<CollectionKeyword> collectionKeywords = collectionKeywordRepository.findByCollectionIdIn(collectionIds);
        Map<Long, Set<Long>> collectionKeywordMap = collectionKeywords.stream()
            .collect(Collectors.groupingBy(
                CollectionKeyword::getCollectionId,
                Collectors.mapping(CollectionKeyword::getKeywordId, Collectors.toSet())
            ));

        // Fliner별 컬렉션 그룹화
        Map<Long, List<CollectionBasicProjection>> flinerCollectionMap = collections.stream()
            .collect(Collectors.groupingBy(CollectionBasicProjection::getUserId));

        // 각 Fliner의 컬렉션 매칭률 계산
        for (FlinerMatch flinerMatch : flinerMatches) {
            List<CollectionBasicProjection> flinerCollections = flinerCollectionMap.getOrDefault(
                flinerMatch.flinerId(), List.of()
            );

            List<CollectionMatch> collectionMatches = flinerCollections.stream()
                .map(collection -> {
                    Set<Long> collectionKeywordIds = collectionKeywordMap.getOrDefault(collection.getId(), Set.of());
                    double overlapRate = calculateOverlapRate(userKeywordIds, collectionKeywordIds);
                    return new CollectionMatch(collection.getId(), overlapRate, collection.getCreatedAt());
                })
                .filter(match -> match.overlapRate() > 0)
                .sorted(Comparator.comparingDouble(CollectionMatch::overlapRate).reversed()
                    .thenComparing(Comparator.comparing(CollectionMatch::createdAt).reversed()))
                .limit(2) // 최대 2개
                .toList();

            flinerMatch.collections().addAll(collectionMatches);
        }
    }

    // 10개 제한 조정 로직
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

    // Jaccard 유사도 기반 중복률 계산
    private double calculateOverlapRate(Set<Long> set1, Set<Long> set2) {
        if (set1.isEmpty() || set2.isEmpty()) {
            return 0.0;
        }

        Set<Long> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);

        Set<Long> union = new HashSet<>(set1);
        union.addAll(set2);

        return (double) intersection.size() / union.size();
    }

    // 내부 DTO
    record FlinerMatch(
        Long flinerId,
        double overlapRate,
        List<CollectionMatch> collections
    ) {}

    record CollectionMatch(
        Long collectionId,
        double overlapRate,
        LocalDateTime createdAt
    ) {}
}
