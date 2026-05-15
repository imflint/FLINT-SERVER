package kr.flint.collection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.EnumSet;
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
