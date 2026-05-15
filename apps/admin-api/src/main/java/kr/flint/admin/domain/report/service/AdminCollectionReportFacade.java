package kr.flint.admin.domain.report.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import kr.flint.admin.common.AdminAuthorizationService;
import kr.flint.admin.domain.report.dto.request.AdminCollectionReportResolutionReq;
import kr.flint.admin.domain.report.dto.response.AdminCollectionReportDetailRes;
import kr.flint.admin.domain.report.dto.response.AdminCollectionReportSummaryRes;
import kr.flint.admin.domain.report.repository.AdminCollectionReportQueryRepository;
import kr.flint.admin.domain.report.repository.AdminCollectionReportQueryRepository.ReportContentRow;
import kr.flint.admin.domain.report.repository.AdminCollectionReportQueryRepository.ReportDetailRow;
import kr.flint.admin.domain.report.repository.AdminCollectionReportQueryRepository.ReportSummaryRow;
import kr.flint.collection.domain.Collection;
import kr.flint.collection.domain.CollectionReport;
import kr.flint.collection.domain.ReportStatus;
import kr.flint.collection.exception.CollectionErrorCode;
import kr.flint.collection.exception.CollectionException;
import kr.flint.collection.repository.CollectionReportRepository;
import kr.flint.collection.service.CollectionService;
import kr.flint.infra.storage.cloudfront.CloudFrontUrlProvider;
import kr.flint.moderation.domain.CollectionModerationAction;
import kr.flint.moderation.domain.ModerationDecision;
import kr.flint.moderation.domain.UserModerationAction;
import kr.flint.moderation.service.ModerationDecisionService;
import kr.flint.shared.dto.PaginationResponse;
import kr.flint.shared.dto.SliceCursor;
import kr.flint.user.service.UserService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminCollectionReportFacade {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final AdminAuthorizationService adminAuthorizationService;
    private final AdminCollectionReportQueryRepository queryRepository;
    private final CollectionReportRepository collectionReportRepository;
    private final CollectionService collectionService;
    private final UserService userService;
    private final CloudFrontUrlProvider cloudFrontUrlProvider;
    private final ModerationDecisionService moderationDecisionService;

    public PaginationResponse<AdminCollectionReportSummaryRes> getReports(
        Long adminUserId,
        ReportStatus status,
        Long cursor,
        Integer size
    ) {
        adminAuthorizationService.validateAdmin(adminUserId);
        int safeSize = normalizeSize(size);
        List<Long> reportIds = queryRepository.findReportIds(status, cursor, safeSize);
        boolean hasNext = reportIds.size() > safeSize;
        List<Long> pageIds = hasNext ? reportIds.subList(0, safeSize) : reportIds;

        Map<Long, CollectionReport> reports = collectionReportRepository.findAllById(pageIds)
            .stream()
            .collect(Collectors.toMap(CollectionReport::getId, Function.identity()));
        List<AdminCollectionReportSummaryRes> data = queryRepository.findReportSummaryRows(pageIds)
            .stream()
            .map(row -> toSummary(row, reports.get(row.reportId())))
            .toList();
        String nextCursor = hasNext && !data.isEmpty() ? String.valueOf(data.getLast().reportId()) : "";
        String currentCursor = cursor != null ? String.valueOf(cursor) : null;
        return PaginationResponse.ofCursor(SliceCursor.of(data, currentCursor, nextCursor));
    }

    public AdminCollectionReportDetailRes getReport(Long adminUserId, Long reportId) {
        adminAuthorizationService.validateAdmin(adminUserId);
        CollectionReport report = collectionService.getReportById(reportId);
        ReportDetailRow row = queryRepository.findReportDetailRow(reportId);
        if (row == null) {
            throw new CollectionException(CollectionErrorCode.COLLECTION_REPORT_NOT_FOUND);
        }
        ModerationDecision decision = moderationDecisionService.findCollectionReportDecision(report.getId()).orElse(null);
        List<AdminCollectionReportDetailRes.ContentInfo> contents = queryRepository.findContentRows(report.getCollectionId())
            .stream()
            .map(this::toContentInfo)
            .toList();
        return toDetail(report, decision, row, contents);
    }

    @Transactional
    public void resolveReport(Long adminUserId, Long reportId, AdminCollectionReportResolutionReq request) {
        adminAuthorizationService.validateAdmin(adminUserId);
        CollectionReport report = collectionService.getReportById(reportId);
        if (report.isResolved()) {
            throw new CollectionException(CollectionErrorCode.COLLECTION_REPORT_ALREADY_RESOLVED);
        }
        Collection collection = collectionService.getCollectionById(report.getCollectionId());

        applyCollectionAction(collection.getId(), request.collectionAction());
        applyUserAction(collection.getUserId(), request.userAction(), request.userActionExpiresAt());
        moderationDecisionService.recordCollectionReportDecision(
            report.getId(),
            collection.getId(),
            collection.getUserId(),
            adminUserId,
            request.collectionAction(),
            request.userAction(),
            request.userActionExpiresAt(),
            request.adminMemo()
        );
        collectionService.resolveReport(reportId, LocalDateTime.now());
    }

    private void applyCollectionAction(Long collectionId, CollectionModerationAction action) {
        switch (action) {
            case DELETE -> collectionService.deleteByAdmin(collectionId);
            case HIDE -> collectionService.hideByAdmin(collectionId);
            case KEEP -> {
            }
        }
    }

    private void applyUserAction(Long userId, UserModerationAction action, LocalDateTime expiresAt) {
        switch (action) {
            case WARN -> userService.warn(userId);
            case RESTRICT_UPLOAD -> userService.restrictUpload(userId, expiresAt);
            case SUSPEND -> userService.suspend(userId, expiresAt);
            case KEEP -> {
            }
        }
    }

    private AdminCollectionReportSummaryRes toSummary(ReportSummaryRow row, CollectionReport report) {
        ReportStatus reportStatus = row.reportStatus() != null ? row.reportStatus() : ReportStatus.PENDING;
        return new AdminCollectionReportSummaryRes(
            row.reportId(),
            row.collectionId(),
            row.collectionTitle(),
            resolveNullableImage(row.collectionImage()),
            row.reporterId(),
            row.reporterNickname(),
            row.ownerId(),
            row.ownerNickname(),
            report != null ? report.getReasons() : java.util.Set.of(),
            report != null ? report.getOtherDetail() : null,
            reportStatus,
            row.createdAt(),
            row.processedAt()
        );
    }

    private AdminCollectionReportDetailRes toDetail(
        CollectionReport report,
        ModerationDecision decision,
        ReportDetailRow row,
        List<AdminCollectionReportDetailRes.ContentInfo> contents
    ) {
        return new AdminCollectionReportDetailRes(
            new AdminCollectionReportDetailRes.ReportInfo(
                report.getId(),
                report.getReasons(),
                report.getOtherDetail(),
                report.getReportStatus() != null ? report.getReportStatus() : ReportStatus.PENDING,
                decision != null ? decision.getCollectionAction() : null,
                decision != null ? decision.getUserAction() : null,
                decision != null ? decision.getUserActionExpiresAt() : null,
                decision != null ? decision.getAdminUserId() : null,
                decision != null ? decision.getAdminMemo() : null,
                report.getCreatedAt(),
                report.getProcessedAt()
            ),
            new AdminCollectionReportDetailRes.CollectionInfo(
                row.collectionId(),
                row.collectionTitle(),
                row.collectionDescription(),
                resolveNullableImage(row.collectionImage()),
                row.isPublic(),
                row.moderationStatus(),
                row.bookmarkCount(),
                row.collectionCreatedAt()
            ),
            new AdminCollectionReportDetailRes.UserInfo(
                row.reporterId(),
                row.reporterNickname(),
                resolveNullableImage(row.reporterProfileImage())
            ),
            new AdminCollectionReportDetailRes.UserInfo(
                row.ownerId(),
                row.ownerNickname(),
                resolveNullableImage(row.ownerProfileImage())
            ),
            contents
        );
    }

    private AdminCollectionReportDetailRes.ContentInfo toContentInfo(ReportContentRow row) {
        return new AdminCollectionReportDetailRes.ContentInfo(
            row.contentId(),
            row.title(),
            resolveNullableImage(row.poster()),
            resolveNullableImage(row.customImage()),
            row.reason(),
            row.isSpoiler()
        );
    }

    private String resolveNullableImage(String imageUrl) {
        return imageUrl == null ? null : cloudFrontUrlProvider.resolveUrl(imageUrl);
    }

    private int normalizeSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
