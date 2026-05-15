package kr.flint.admin.domain.report.dto.response;

import java.time.LocalDateTime;
import java.util.Set;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.flint.collection.domain.ReportReason;
import kr.flint.collection.domain.ReportStatus;

@Schema(description = "컬렉션 신고 목록 항목")
public record AdminCollectionReportSummaryRes(
    Long reportId,
    Long collectionId,
    String collectionTitle,
    String collectionImageUrl,
    Long reporterId,
    String reporterNickname,
    Long ownerId,
    String ownerNickname,
    Set<ReportReason> reasons,
    String otherDetail,
    ReportStatus reportStatus,
    LocalDateTime createdAt,
    LocalDateTime processedAt
) {
}
