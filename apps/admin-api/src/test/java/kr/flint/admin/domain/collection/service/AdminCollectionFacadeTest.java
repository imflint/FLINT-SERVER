package kr.flint.admin.domain.collection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import kr.flint.admin.common.AdminAuthorizationService;
import kr.flint.admin.domain.collection.dto.request.AdminCollectionContentUpdateReq;
import kr.flint.admin.domain.collection.dto.request.AdminCollectionUpdateReq;
import kr.flint.admin.domain.collection.dto.request.AdminCollectionVisibility;
import kr.flint.admin.domain.collection.repository.AdminCollectionQueryRepository;
import kr.flint.admin.domain.collection.repository.AdminCollectionQueryRepository.CollectionContentRow;
import kr.flint.admin.domain.collection.repository.AdminCollectionQueryRepository.CollectionDetailRow;
import kr.flint.admin.domain.collection.repository.AdminCollectionQueryRepository.CollectionSummaryRow;
import kr.flint.collection.domain.CollectionModerationStatus;
import kr.flint.collection.dto.CollectionUpdateCommand;
import kr.flint.collection.service.CollectionService;
import kr.flint.content.domain.Content;
import kr.flint.content.domain.MediaType;
import kr.flint.content.service.ContentService;
import kr.flint.infra.storage.cloudfront.CloudFrontUrlProvider;

@ExtendWith(MockitoExtension.class)
class AdminCollectionFacadeTest {

    @Mock
    private AdminAuthorizationService adminAuthorizationService;

    @Mock
    private AdminCollectionQueryRepository queryRepository;

    @Mock
    private CollectionService collectionService;

    @Mock
    private ContentService contentService;

    @Mock
    private CloudFrontUrlProvider cloudFrontUrlProvider;

    @InjectMocks
    private AdminCollectionFacade facade;

    @Test
    @DisplayName("컬렉션 목록은 cursor 페이지와 필터를 적용해 조회")
    void getCollections() {
        LocalDateTime createdAt = LocalDateTime.now();
        when(queryRepository.findCollectionIds("추천", AdminCollectionVisibility.PUBLIC, CollectionModerationStatus.VISIBLE, null, 20))
            .thenReturn(List.of(2L, 1L));
        when(queryRepository.findCollectionSummaryRows(List.of(2L, 1L))).thenReturn(List.of(
            new CollectionSummaryRow(2L, "추천 컬렉션", "설명", "image.jpg", true, CollectionModerationStatus.VISIBLE, 3, 10L, "운영자", 2L, createdAt),
            new CollectionSummaryRow(1L, "지난 컬렉션", "설명", null, true, CollectionModerationStatus.VISIBLE, 1, 11L, "작성자", 1L, createdAt)
        ));
        when(cloudFrontUrlProvider.resolveUrl("image.jpg")).thenReturn("https://cdn/image.jpg");

        var result = facade.getCollections(99L, "추천", AdminCollectionVisibility.PUBLIC, CollectionModerationStatus.VISIBLE, null, null);

        assertThat(result.data()).hasSize(2);
        assertThat(result.data().getFirst().collectionId()).isEqualTo(2L);
        assertThat(result.data().getFirst().contentCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("컬렉션 수정은 포함 콘텐츠 전체를 교체하고 수정 후 상세를 반환")
    void updateCollection() {
        LocalDateTime createdAt = LocalDateTime.now();
        Content content = Content.create(100L, MediaType.MOVIE, "인셉션", 2010, "감독", "설명", "poster.jpg");
        AdminCollectionUpdateReq request = new AdminCollectionUpdateReq(
            null,
            "새 컬렉션",
            "새 설명",
            true,
            List.of(new AdminCollectionContentUpdateReq(1L, false, "좋아요", null))
        );
        when(contentService.getContentById(1L)).thenReturn(content);
        when(queryRepository.findCollectionDetailRow(10L)).thenReturn(
            new CollectionDetailRow(10L, "새 컬렉션", "새 설명", "poster.jpg", true, CollectionModerationStatus.VISIBLE, 0, createdAt, 20L, "작성자", null)
        );
        when(queryRepository.findCollectionContentRows(10L)).thenReturn(List.of(
            new CollectionContentRow(1L, "인셉션", "poster.jpg", null, false, "좋아요", 2010, MediaType.MOVIE)
        ));
        when(cloudFrontUrlProvider.resolveUrl("poster.jpg")).thenReturn("https://cdn/poster.jpg");

        var result = facade.updateCollection(99L, 10L, request);

        verify(contentService).validateContentIdsExist(List.of(1L));
        verify(collectionService).updateCollectionByAdmin(eq(10L), any(CollectionUpdateCommand.class), eq("poster.jpg"));
        assertThat(result.collectionId()).isEqualTo(10L);
        assertThat(result.contents()).hasSize(1);
    }
}
