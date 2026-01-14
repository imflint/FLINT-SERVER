package kr.flint.api.domain.home;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.flint.api.domain.home.dto.projection.CollectionCardProjection;
import kr.flint.api.domain.home.dto.response.CollectionCardRes;
import kr.flint.api.domain.home.dto.response.RecommendedCollectionsRes;
import kr.flint.api.domain.home.port.CollectionRecommendationPort;
import kr.flint.api.domain.home.repository.HomeCollectionRepository;
import kr.flint.api.domain.home.service.RecommendationCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeQueryFacade {

    private static final int MAX_RECOMMENDED_COLLECTIONS = 10;

    private final CollectionRecommendationPort recommendationPort;
    private final HomeCollectionRepository homeCollectionRepository;
    private final RecommendationCacheService cacheService;

    // 추천 컬렉션 조회 (캐시 적용)
    public RecommendedCollectionsRes getRecommendedCollections(Long userId) {
        // 캐시 확인
        Optional<List<Long>> cached = cacheService.getCachedRecommendations(userId);
        List<Long> collectionIds;

        if (cached.isPresent()) {
            // 캐시 히트 - 순서 셔플
            collectionIds = cacheService.getShuffledOrder(cached.get());
            log.debug("캐시 히트. userId={}", userId);
        } else {
            // 캐시 미스 - 추천 로직 실행 후 캐싱
            collectionIds = recommendationPort.recommend(userId, MAX_RECOMMENDED_COLLECTIONS);
            if (!collectionIds.isEmpty()) {
                cacheService.cacheRecommendations(userId, collectionIds);
            }
            log.debug("캐시 미스. userId={}, recommendations={}", userId, collectionIds.size());
        }

        if (collectionIds.isEmpty()) {
            return RecommendedCollectionsRes.from(List.of());
        }

        // 컬렉션 + 작성자 정보 조회
        List<CollectionCardProjection> projections = homeCollectionRepository.findCollectionCardsWithUser(collectionIds);

        // 추천 순서 유지를 위해 정렬
        Map<Long, CollectionCardProjection> projectionMap = projections.stream()
            .collect(Collectors.toMap(CollectionCardProjection::getId, Function.identity()));

        List<CollectionCardRes> orderedCards = collectionIds.stream()
            .filter(projectionMap::containsKey)
            .map(id -> CollectionCardRes.from(projectionMap.get(id)))
            .toList();

        return RecommendedCollectionsRes.from(orderedCards);
    }
}
