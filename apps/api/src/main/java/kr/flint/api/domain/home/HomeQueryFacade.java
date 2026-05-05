package kr.flint.api.domain.home;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.flint.api.domain.home.dto.projection.CollectionCardDto;
import kr.flint.api.domain.home.dto.projection.CollectionContentImageDto;
import kr.flint.api.domain.home.dto.response.CollectionCardRes;
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

    private static final int MAX_RECOMMENDED_COLLECTIONS = 10;

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

    private Map<Long, List<String>> buildContentImagesMap(List<Long> collectionIds) {
        List<CollectionContentImageDto> contentImages = homeCollectionRepository.findContentImagesByCollectionIds(collectionIds);

        return contentImages.stream()
            .collect(Collectors.groupingBy(
                CollectionContentImageDto::collectionId,
                Collectors.mapping(CollectionContentImageDto::image, Collectors.toCollection(ArrayList::new))
            ));
    }
}
