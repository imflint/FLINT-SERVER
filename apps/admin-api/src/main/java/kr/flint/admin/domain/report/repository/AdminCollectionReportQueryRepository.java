package kr.flint.admin.domain.report.repository;

import static kr.flint.collection.domain.QCollection.collection;
import static kr.flint.collection.domain.QCollectionContent.collectionContent;
import static kr.flint.collection.domain.QCollectionReport.collectionReport;
import static kr.flint.content.domain.QContent.content;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;

import kr.flint.collection.domain.CollectionModerationStatus;
import kr.flint.collection.domain.ReportStatus;
import kr.flint.user.domain.QUser;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class AdminCollectionReportQueryRepository {

    private final JPAQueryFactory queryFactory;

    public List<Long> findReportIds(ReportStatus status, Long cursor, int size) {
        return queryFactory
            .select(collectionReport.id)
            .from(collectionReport)
            .where(
                statusCondition(status),
                cursor != null ? collectionReport.id.lt(cursor) : null
            )
            .orderBy(collectionReport.id.desc())
            .limit(size + 1L)
            .fetch();
    }

    public List<ReportSummaryRow> findReportSummaryRows(List<Long> reportIds) {
        if (reportIds == null || reportIds.isEmpty()) {
            return List.of();
        }
        QUser reporter = new QUser("reporter");
        QUser owner = new QUser("owner");
        return queryFactory
            .select(Projections.constructor(
                ReportSummaryRow.class,
                collectionReport.id,
                collection.id,
                collection.title,
                collection.image,
                reporter.id,
                reporter.nickname,
                owner.id,
                owner.nickname,
                collectionReport.reportStatus,
                collectionReport.createdAt,
                collectionReport.processedAt
            ))
            .from(collectionReport)
            .leftJoin(collection).on(collection.id.eq(collectionReport.collectionId))
            .leftJoin(reporter).on(reporter.id.eq(collectionReport.reporterId))
            .leftJoin(owner).on(owner.id.eq(collection.userId))
            .where(collectionReport.id.in(reportIds))
            .orderBy(collectionReport.id.desc())
            .fetch();
    }

    public ReportDetailRow findReportDetailRow(Long reportId) {
        QUser reporter = new QUser("reporter");
        QUser owner = new QUser("owner");
        return queryFactory
            .select(Projections.constructor(
                ReportDetailRow.class,
                collectionReport.id,
                collection.id,
                collection.title,
                collection.description,
                collection.image,
                collection.isPublic,
                collection.moderationStatus,
                collection.bookmarkCount,
                collection.createdAt,
                reporter.id,
                reporter.nickname,
                reporter.profileImage,
                owner.id,
                owner.nickname,
                owner.profileImage
            ))
            .from(collectionReport)
            .leftJoin(collection).on(collection.id.eq(collectionReport.collectionId))
            .leftJoin(reporter).on(reporter.id.eq(collectionReport.reporterId))
            .leftJoin(owner).on(owner.id.eq(collection.userId))
            .where(collectionReport.id.eq(reportId))
            .fetchOne();
    }

    public List<ReportContentRow> findContentRows(Long collectionId) {
        return queryFactory
            .select(Projections.constructor(
                ReportContentRow.class,
                content.id,
                content.title,
                content.poster,
                collectionContent.customImage,
                collectionContent.reason,
                collectionContent.isSpoiler
            ))
            .from(collectionContent)
            .join(content).on(content.id.eq(collectionContent.contentId))
            .where(collectionContent.collection.id.eq(collectionId))
            .orderBy(collectionContent.id.asc())
            .fetch();
    }

    private BooleanExpression statusCondition(ReportStatus status) {
        if (status == null) {
            return null;
        }
        if (status == ReportStatus.PENDING) {
            return collectionReport.reportStatus.isNull()
                .or(collectionReport.reportStatus.eq(ReportStatus.PENDING));
        }
        return collectionReport.reportStatus.eq(status);
    }

    public record ReportSummaryRow(
        Long reportId,
        Long collectionId,
        String collectionTitle,
        String collectionImage,
        Long reporterId,
        String reporterNickname,
        Long ownerId,
        String ownerNickname,
        ReportStatus reportStatus,
        LocalDateTime createdAt,
        LocalDateTime processedAt
    ) {
    }

    public record ReportDetailRow(
        Long reportId,
        Long collectionId,
        String collectionTitle,
        String collectionDescription,
        String collectionImage,
        boolean isPublic,
        CollectionModerationStatus moderationStatus,
        int bookmarkCount,
        LocalDateTime collectionCreatedAt,
        Long reporterId,
        String reporterNickname,
        String reporterProfileImage,
        Long ownerId,
        String ownerNickname,
        String ownerProfileImage
    ) {
    }

    public record ReportContentRow(
        Long contentId,
        String title,
        String poster,
        String customImage,
        String reason,
        boolean isSpoiler
    ) {
    }
}
