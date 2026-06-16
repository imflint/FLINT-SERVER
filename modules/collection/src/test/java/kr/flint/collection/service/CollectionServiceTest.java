package kr.flint.collection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import kr.flint.collection.domain.Collection;
import kr.flint.collection.domain.CollectionContent;
import kr.flint.collection.domain.CollectionContentImage;
import kr.flint.collection.domain.CollectionModerationStatus;
import kr.flint.collection.domain.CollectionReport;
import kr.flint.collection.domain.ReportReason;
import kr.flint.collection.dto.CollectionCreateCommand;
import kr.flint.collection.dto.CollectionCreateCommand.ContentInput;
import kr.flint.collection.dto.CollectionUpdateCommand;
import kr.flint.collection.exception.CollectionErrorCode;
import kr.flint.collection.exception.CollectionException;
import kr.flint.collection.repository.CollectionContentImageRepository;
import kr.flint.collection.repository.CollectionContentRepository;
import kr.flint.collection.repository.CollectionReportRepository;
import kr.flint.collection.repository.CollectionRepository;
import kr.flint.collection.repository.RecentViewedCollectionRepository;

@ExtendWith(MockitoExtension.class)
class CollectionServiceTest {

    @Mock
    private CollectionRepository collectionRepository;

    @Mock
    private CollectionContentRepository collectionContentRepository;

    @Mock
    private CollectionContentImageRepository collectionContentImageRepository;

    @Mock
    private CollectionReportRepository collectionReportRepository;

    @Mock
    private RecentViewedCollectionRepository recentViewedCollectionRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private CollectionService collectionService;

    @Nested
    @DisplayName("createCollection")
    class CreateCollection {

        @Test
        @DisplayName("작품별 커스텀 이미지를 요청 순서대로 저장")
        @SuppressWarnings("unchecked")
        void saveContentImagesInRequestOrder() {
            Collection savedCollection = Collection.create("제목", "설명", "cover.jpg", true, 1L);
            ReflectionTestUtils.setField(savedCollection, "id", 10L);
            when(collectionRepository.save(any(Collection.class))).thenReturn(savedCollection);

            collectionService.createCollection(1L, CollectionCreateCommand.of(
                "제목",
                "설명",
                "cover.jpg",
                true,
                List.of(ContentInput.of(100L, false, "좋아요", List.of("image-a.jpg", "image-b.jpg")))
            ), "cover.jpg");

            ArgumentCaptor<Iterable<CollectionContentImage>> imageCaptor = ArgumentCaptor.forClass(Iterable.class);
            verify(collectionContentImageRepository).saveAll(imageCaptor.capture());

            List<CollectionContentImage> savedImages = toList(imageCaptor.getValue());
            assertThat(savedImages).hasSize(2);
            assertThat(savedImages)
                .extracting(CollectionContentImage::getImageKey)
                .containsExactly("image-a.jpg", "image-b.jpg");
            assertThat(savedImages)
                .extracting(CollectionContentImage::getSortOrder)
                .containsExactly(0, 1);
        }

        @Test
        @DisplayName("컬렉션 작품을 요청 순서대로 저장")
        @SuppressWarnings("unchecked")
        void saveCollectionContentsInRequestOrder() {
            Collection savedCollection = Collection.create("제목", "설명", "cover.jpg", true, 1L);
            ReflectionTestUtils.setField(savedCollection, "id", 10L);
            when(collectionRepository.save(any(Collection.class))).thenReturn(savedCollection);

            collectionService.createCollection(1L, CollectionCreateCommand.of(
                "제목",
                "설명",
                "cover.jpg",
                true,
                List.of(
                    ContentInput.of(100L, false, "첫 번째", List.of()),
                    ContentInput.of(200L, true, "두 번째", List.of()),
                    ContentInput.of(300L, false, "세 번째", List.of())
                )
            ), "cover.jpg");

            ArgumentCaptor<Iterable<CollectionContent>> contentCaptor = ArgumentCaptor.forClass(Iterable.class);
            verify(collectionContentRepository).saveAll(contentCaptor.capture());

            List<CollectionContent> savedContents = toList(contentCaptor.getValue());
            assertThat(savedContents)
                .extracting(CollectionContent::getContentId)
                .containsExactly(100L, 200L, 300L);
            assertThat(savedContents)
                .extracting(CollectionContent::getSortOrder)
                .containsExactly(0, 1, 2);
        }
    }

    @Nested
    @DisplayName("admin moderation")
    class AdminModeration {

        @Test
        @DisplayName("HIDE 조치는 컬렉션을 숨김 상태로 변경")
        void hideCollection() {
            Collection collection = Collection.create("제목", "설명", "image.jpg", true, 1L);
            ReflectionTestUtils.setField(collection, "id", 10L);
            when(collectionRepository.findById(10L)).thenReturn(Optional.of(collection));

            collectionService.hideByAdmin(10L);

            assertThat(collection.getModerationStatus()).isEqualTo(CollectionModerationStatus.HIDDEN);
        }

        @Test
        @DisplayName("DELETE 조치는 컬렉션을 삭제 상태로 변경")
        void deleteCollection() {
            Collection collection = Collection.create("제목", "설명", "image.jpg", true, 1L);
            ReflectionTestUtils.setField(collection, "id", 10L);
            when(collectionRepository.findById(10L)).thenReturn(Optional.of(collection));

            collectionService.deleteByAdmin(10L);

            assertThat(collection.getModerationStatus()).isEqualTo(CollectionModerationStatus.DELETED);
        }

        @Test
        @DisplayName("관리자 수정은 소유자 검증 없이 컬렉션과 포함 콘텐츠를 교체")
        @SuppressWarnings("unchecked")
        void updateCollectionByAdmin() {
            Collection collection = Collection.create("제목", "설명", "image.jpg", true, 1L);
            ReflectionTestUtils.setField(collection, "id", 10L);
            when(collectionRepository.findById(10L)).thenReturn(Optional.of(collection));
            when(collectionContentRepository.findContentIdsByCollectionId(10L)).thenReturn(List.of(1L));

            collectionService.updateCollectionByAdmin(10L, CollectionUpdateCommand.of(
                "새 제목",
                "새 설명",
                "new.jpg",
                false,
                List.of(ContentInput.of(2L, false, "새 메모", List.of("new-content.jpg")))
            ), "new.jpg");

            assertThat(collection.getTitle()).isEqualTo("새 제목");
            assertThat(collection.getDescription()).isEqualTo("새 설명");
            assertThat(collection.isPublic()).isFalse();
            verify(collectionContentImageRepository).deleteAllByCollectionContentCollection(collection);
            verify(collectionContentRepository).deleteAllByCollection(collection);
            ArgumentCaptor<Iterable<CollectionContent>> contentCaptor = ArgumentCaptor.forClass(Iterable.class);
            verify(collectionContentRepository).saveAll(contentCaptor.capture());
            assertThat(toList(contentCaptor.getValue()))
                .extracting(CollectionContent::getContentId, CollectionContent::getSortOrder)
                .containsExactly(tuple(2L, 0));
        }
    }

    @Nested
    @DisplayName("saveRecentCollection")
    class SaveRecentCollection {

        @Test
        @DisplayName("최근 본 컬렉션은 native upsert로 저장")
        void upsertRecentCollection() {
            Collection collection = Collection.create("제목", "설명", "image.jpg", true, 1L);
            ReflectionTestUtils.setField(collection, "id", 10L);
            when(collectionRepository.findById(10L)).thenReturn(Optional.of(collection));

            collectionService.saveRecentCollection(1L, 10L);

            verify(recentViewedCollectionRepository).upsertRecentViewedCollection(
                anyLong(),
                eq(1L),
                eq(10L),
                any(LocalDateTime.class)
            );
        }

        @Test
        @DisplayName("존재하지 않는 컬렉션이면 최근 조회 기록을 저장하지 않음")
        void collectionNotFound() {
            when(collectionRepository.findById(10L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> collectionService.saveRecentCollection(1L, 10L))
                .isInstanceOf(CollectionException.class)
                .extracting("errorCode")
                .isEqualTo(CollectionErrorCode.COLLECTION_NOT_FOUND);
            verify(recentViewedCollectionRepository, never()).upsertRecentViewedCollection(
                anyLong(),
                anyLong(),
                anyLong(),
                any(LocalDateTime.class)
            );
        }
    }

    @Nested
    @DisplayName("resolveReport")
    class ResolveReport {

        @Test
        @DisplayName("이미 처리된 신고는 재처리할 수 없음")
        void alreadyResolved() {
            CollectionReport report = CollectionReport.create(1L, 10L, EnumSet.of(ReportReason.SPAM), null);
            ReflectionTestUtils.setField(report, "id", 100L);
            report.resolve(LocalDateTime.now());
            when(collectionReportRepository.findById(100L)).thenReturn(Optional.of(report));

            assertThatThrownBy(() -> collectionService.resolveReport(
                100L,
                LocalDateTime.now()
            ))
                .isInstanceOf(CollectionException.class)
                .extracting("errorCode")
                .isEqualTo(CollectionErrorCode.COLLECTION_REPORT_ALREADY_RESOLVED);
        }
    }

    private <T> List<T> toList(Iterable<T> values) {
        List<T> result = new ArrayList<>();
        values.forEach(result::add);
        return result;
    }
}
