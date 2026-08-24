package kr.flint.api.domain.home;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import kr.flint.api.domain.home.dto.projection.CollectionCardDto;
import kr.flint.api.domain.home.dto.projection.CollectionContentImageDto;
import kr.flint.api.domain.home.dto.response.CollectionCardRes;
import kr.flint.api.domain.home.dto.response.PopularCollectionCardRes;
import kr.flint.api.domain.home.dto.response.PopularCollectionsRes;
import kr.flint.api.domain.home.dto.response.RecommendedCollectionsRes;
import kr.flint.api.domain.home.port.CollectionRecommendationPort;
import kr.flint.api.domain.home.repository.HomeCollectionRepository;
import kr.flint.bookmark.service.BookmarkQueryService;
import kr.flint.infra.storage.cloudfront.CloudFrontUrlProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeQueryFacade {

    private static final int MAX_RECOMMENDED_COLLECTIONS = 5;
    private static final int MAX_POPULAR_COLLECTIONS = 10;
    private static final int POPULAR_WINDOW_DAYS = 7;

    private final CollectionRecommendationPort recommendationPort;
    private final HomeCollectionRepository homeCollectionRepository;
    private final BookmarkQueryService bookmarkQueryService;
    private final CloudFrontUrlProvider cloudFrontUrlProvider;

    // 추천 컬렉션 조회
    public RecommendedCollectionsRes getRecommendedCollections(Long userId) {
        List<Long> collectionIds = recommendationPort.recommend(userId, MAX_RECOMMENDED_COLLECTIONS);
        log.debug("추천 컬렉션 조회. userId={}, count={}", userId, collectionIds.size());

        if (collectionIds.isEmpty()) {
            return RecommendedCollectionsRes.from(List.of());
        }

        List<CollectionCardDto> collections = homeCollectionRepository.findCollectionCardsWithUser(collectionIds);

        Map<Long, List<String>> contentImagesMap = buildContentImagesMap(collectionIds);

        Set<Long> bookmarkedIds = bookmarkQueryService.getBookmarkedCollectionIdSet(userId);

        Map<Long, CollectionCardDto> collectionMap = collections.stream()
            .collect(Collectors.toMap(CollectionCardDto::id, Function.identity()));

        List<CollectionCardRes> orderedCards = collectionIds.stream()
            .filter(collectionMap::containsKey)
            .map(id -> CollectionCardRes.from(
                collectionMap.get(id),
                contentImagesMap.getOrDefault(id, List.of()),
                bookmarkedIds.contains(id),
                cloudFrontUrlProvider::resolveUrl
            ))
            .toList();

        return RecommendedCollectionsRes.from(orderedCards);
    }

    // 인기 컬렉션 조회 (최근 7일 북마크 증가량 우선, 동률/0건은 총 북마크 수로 fallback)
    public PopularCollectionsRes getPopularCollections() {
        LocalDateTime since = LocalDateTime.now().minusDays(POPULAR_WINDOW_DAYS);
        List<Long> collectionIds = homeCollectionRepository.findWeeklyPopularPublicCollectionIds(since, MAX_POPULAR_COLLECTIONS);
        log.debug("인기 컬렉션 조회. since={}, count={}", since, collectionIds.size());

        if (collectionIds.isEmpty()) {
            return PopularCollectionsRes.from(List.of());
        }

        List<CollectionCardDto> cards = homeCollectionRepository.findCollectionCardsWithUser(collectionIds);
        Map<Long, List<String>> contentImagesMap = buildContentImagesMap(collectionIds);
        Map<Long, CollectionCardDto> cardMap = cards.stream()
            .collect(Collectors.toMap(CollectionCardDto::id, Function.identity()));

        List<PopularCollectionCardRes> ordered = collectionIds.stream()
            .map(cardMap::get)
            .filter(java.util.Objects::nonNull)
            .map(dto -> PopularCollectionCardRes.from(
                dto,
                contentImagesMap.getOrDefault(dto.id(), List.of()),
                cloudFrontUrlProvider::resolveUrl
            ))
            .toList();

        return PopularCollectionsRes.from(ordered);
    }

    private Map<Long, List<String>> buildContentImagesMap(List<Long> collectionIds) {
        List<CollectionContentImageDto> contentImages = homeCollectionRepository.findContentImagesByCollectionIds(collectionIds);

        return contentImages.stream()
            .collect(Collectors.groupingBy(
                CollectionContentImageDto::collectionId,
                Collectors.mapping(
                    CollectionContentImageDto::poster,
                    Collectors.filtering(StringUtils::hasText, Collectors.toCollection(ArrayList::new))
                )
            ));
    }
}
