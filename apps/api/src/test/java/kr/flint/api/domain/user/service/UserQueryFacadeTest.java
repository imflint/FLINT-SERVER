package kr.flint.api.domain.user.service;

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

import kr.flint.api.domain.bookmark.repository.BookmarkQueryRepository;
import kr.flint.api.domain.user.dto.response.CollectionContentImageDto;
import kr.flint.api.domain.user.dto.response.CollectionWithUserDto;
import kr.flint.api.domain.user.dto.response.UserCollectionsRes;
import kr.flint.api.domain.user.repository.UserCollectionRepository;
import kr.flint.bookmark.service.BookmarkQueryService;
import kr.flint.infra.gpt.service.ChatService;
import kr.flint.infra.storage.cloudfront.CloudFrontUrlProvider;
import kr.flint.taste.service.TasteService;
import kr.flint.user.service.UserService;

@ExtendWith(MockitoExtension.class)
class UserQueryFacadeTest {

    @Mock
    private UserService userService;

    @Mock
    private BookmarkQueryService bookmarkQueryService;

    @Mock
    private UserCollectionRepository userCollectionRepository;

    @Mock
    private BookmarkQueryRepository bookmarkQueryRepository;

    @Mock
    private ChatService chatService;

    @Mock
    private TasteService tasteService;

    @Mock
    private UserCommandFacade userCommandFacade;

    @Mock
    private CloudFrontUrlProvider cloudFrontUrlProvider;

    @InjectMocks
    private UserQueryFacade userQueryFacade;

    @Nested
    @DisplayName("getMyCollections")
    class GetMyCollections {

        @Test
        @DisplayName("콘텐츠 이미지가 null이면 사용자 컬렉션 이미지 목록에서 제외")
        void excludeNullContentImage() {
            // given
            Long userId = 1L;
            Long collectionId = 10L;
            when(userCollectionRepository.findAllCollectionsWithUserByUserId(userId))
                .thenReturn(List.of(new CollectionWithUserDto(
                    collectionId,
                    "컬렉션 제목",
                    "컬렉션 설명",
                    "collection.jpg",
                    3,
                    userId,
                    "profile.jpg",
                    "플린트"
                )));
            when(userCollectionRepository.findContentImagesByCollectionIds(List.of(collectionId)))
                .thenReturn(List.of(
                    new CollectionContentImageDto(collectionId, null),
                    new CollectionContentImageDto(collectionId, "poster.jpg")
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
            UserCollectionsRes response = userQueryFacade.getMyCollections(userId);

            // then
            assertThat(response.collections().getFirst().imageList()).containsExactly("resolved/poster.jpg");
            verify(cloudFrontUrlProvider, never()).resolveUrl(isNull());
        }
    }
}
