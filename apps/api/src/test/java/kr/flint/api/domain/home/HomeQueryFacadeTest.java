package kr.flint.api.domain.home;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import kr.flint.api.domain.home.dto.projection.CollectionCardDto;
import kr.flint.api.domain.home.dto.projection.CollectionContentImageDto;
import kr.flint.api.domain.home.dto.response.RecommendedCollectionsRes;
import kr.flint.api.domain.home.port.CollectionRecommendationPort;
import kr.flint.api.domain.home.repository.HomeCollectionRepository;
import kr.flint.bookmark.service.BookmarkQueryService;
import kr.flint.infra.storage.cloudfront.CloudFrontUrlProvider;

@ExtendWith(MockitoExtension.class)
class HomeQueryFacadeTest {

    @Mock
    private CollectionRecommendationPort recommendationPort;

    @Mock
    private HomeCollectionRepository homeCollectionRepository;

    @Mock
    private BookmarkQueryService bookmarkQueryService;

    @Mock
    private CloudFrontUrlProvider cloudFrontUrlProvider;

    @InjectMocks
    private HomeQueryFacade homeQueryFacade;

    @Nested
    @DisplayName("getRecommendedCollections")
    class GetRecommendedCollections {

        @Test
        @DisplayName("콘텐츠 이미지가 null이면 추천 컬렉션 이미지 목록에서 제외")
        void excludeNullContentImage() {
            // given
            Long userId = 1L;
            Long collectionId = 10L;
            when(recommendationPort.recommend(userId, 5)).thenReturn(List.of(collectionId));
            when(homeCollectionRepository.findCollectionCardsWithUser(List.of(collectionId)))
                .thenReturn(List.of(new CollectionCardDto(
                    collectionId,
                    "컬렉션 제목",
                    "컬렉션 설명",
                    "collection.jpg",
                    3,
                    100L,
                    "profile.jpg",
                    "플린트"
                )));
            when(homeCollectionRepository.findContentImagesByCollectionIds(List.of(collectionId)))
                .thenReturn(List.of(
                    new CollectionContentImageDto(collectionId, null, null),
                    new CollectionContentImageDto(collectionId, null, "poster.jpg")
                ));
            when(bookmarkQueryService.getBookmarkedCollectionIdSet(userId)).thenReturn(Set.of());
            when(cloudFrontUrlProvider.resolveUrl(nullable(String.class)))
                .thenAnswer(invocation -> {
                    String imageUrl = invocation.getArgument(0, String.class);
                    if (imageUrl == null || imageUrl.isBlank()) {
                        throw new NullPointerException("image key must not be blank");
                    }
                    return "resolved/" + imageUrl;
                });

            // when
            RecommendedCollectionsRes response = homeQueryFacade.getRecommendedCollections(userId);

            // then
            assertThat(response.collections().getFirst().imageList()).containsExactly("resolved/poster.jpg");
            verify(cloudFrontUrlProvider, never()).resolveUrl(isNull());
        }
    }
}
