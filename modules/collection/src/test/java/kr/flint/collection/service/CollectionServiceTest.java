package kr.flint.collection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import kr.flint.collection.domain.Collection;
import kr.flint.collection.domain.CollectionModerationStatus;
import kr.flint.collection.domain.CollectionReport;
import kr.flint.collection.domain.ReportReason;
import kr.flint.collection.dto.CollectionCreateCommand.ContentInput;
import kr.flint.collection.dto.CollectionUpdateCommand;
import kr.flint.collection.exception.CollectionErrorCode;
import kr.flint.collection.exception.CollectionException;
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
    private CollectionReportRepository collectionReportRepository;

    @Mock
    private RecentViewedCollectionRepository recentViewedCollectionRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private CollectionService collectionService;

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
                List.of(ContentInput.of(2L, false, "새 메모", null))
            ), "new.jpg");

            assertThat(collection.getTitle()).isEqualTo("새 제목");
            assertThat(collection.getDescription()).isEqualTo("새 설명");
            assertThat(collection.isPublic()).isFalse();
            verify(collectionContentRepository).deleteAllByCollection(collection);
            verify(collectionContentRepository).saveAll(any());
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
}
