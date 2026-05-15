package kr.flint.admin.domain.report.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.EnumSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import kr.flint.admin.common.AdminAuthorizationService;
import kr.flint.admin.domain.report.dto.request.AdminCollectionReportResolutionReq;
import kr.flint.admin.domain.report.repository.AdminCollectionReportQueryRepository;
import kr.flint.collection.domain.Collection;
import kr.flint.collection.domain.CollectionReport;
import kr.flint.collection.domain.ReportReason;
import kr.flint.collection.exception.CollectionErrorCode;
import kr.flint.collection.exception.CollectionException;
import kr.flint.collection.repository.CollectionReportRepository;
import kr.flint.collection.service.CollectionService;
import kr.flint.infra.storage.cloudfront.CloudFrontUrlProvider;
import kr.flint.moderation.domain.CollectionModerationAction;
import kr.flint.moderation.domain.UserModerationAction;
import kr.flint.moderation.service.ModerationDecisionService;
import kr.flint.user.service.UserService;

@ExtendWith(MockitoExtension.class)
class AdminCollectionReportFacadeTest {

    @Mock
    private AdminAuthorizationService adminAuthorizationService;

    @Mock
    private AdminCollectionReportQueryRepository queryRepository;

    @Mock
    private CollectionReportRepository collectionReportRepository;

    @Mock
    private CollectionService collectionService;

    @Mock
    private UserService userService;

    @Mock
    private CloudFrontUrlProvider cloudFrontUrlProvider;

    @Mock
    private ModerationDecisionService moderationDecisionService;

    @InjectMocks
    private AdminCollectionReportFacade facade;

    @Test
    @DisplayName("신고 처리 시 컬렉션 조치와 사용자 조치를 함께 적용")
    void resolveReport() {
        CollectionReport report = CollectionReport.create(1L, 10L, EnumSet.of(ReportReason.SPAM), null);
        ReflectionTestUtils.setField(report, "id", 100L);
        Collection collection = Collection.create("제목", "설명", "image.jpg", true, 20L);
        ReflectionTestUtils.setField(collection, "id", 10L);
        when(collectionService.getReportById(100L)).thenReturn(report);
        when(collectionService.getCollectionById(10L)).thenReturn(collection);

        facade.resolveReport(99L, 100L, new AdminCollectionReportResolutionReq(
            CollectionModerationAction.HIDE,
            UserModerationAction.WARN,
            null,
            "처리"
        ));

        verify(collectionService).hideByAdmin(10L);
        verify(userService).warn(20L);
        verify(moderationDecisionService).recordCollectionReportDecision(
            eq(100L),
            eq(10L),
            eq(20L),
            eq(99L),
            eq(CollectionModerationAction.HIDE),
            eq(UserModerationAction.WARN),
            isNull(),
            eq("처리")
        );
        verify(collectionService).resolveReport(eq(100L), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("이미 처리된 신고는 조치를 적용하지 않음")
    void alreadyResolved() {
        CollectionReport report = CollectionReport.create(1L, 10L, EnumSet.of(ReportReason.SPAM), null);
        ReflectionTestUtils.setField(report, "id", 100L);
        report.resolve(LocalDateTime.now());
        when(collectionService.getReportById(100L)).thenReturn(report);

        assertThatThrownBy(() -> facade.resolveReport(99L, 100L, new AdminCollectionReportResolutionReq(
            CollectionModerationAction.DELETE,
            UserModerationAction.SUSPEND,
            null,
            "재처리"
        )))
            .isInstanceOf(CollectionException.class)
            .extracting("errorCode")
            .isEqualTo(CollectionErrorCode.COLLECTION_REPORT_ALREADY_RESOLVED);
        verify(collectionService, never()).deleteByAdmin(10L);
        verify(userService, never()).suspend(20L, null);
    }
}
