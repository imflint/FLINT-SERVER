package kr.flint.admin.domain.report.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.flint.collection.domain.CollectionModerationStatus;
import kr.flint.collection.domain.ReportReason;
import kr.flint.collection.domain.ReportStatus;
import kr.flint.moderation.domain.CollectionModerationAction;
import kr.flint.moderation.domain.UserModerationAction;

@Schema(description = "컬렉션 신고 상세")
public record AdminCollectionReportDetailRes(
    ReportInfo report,
    CollectionInfo collection,
    UserInfo reporter,
    UserInfo owner,
    List<ContentInfo> contents
) {
    public record ReportInfo(
        Long reportId,
        Set<ReportReason> reasons,
        String otherDetail,
        ReportStatus reportStatus,
        CollectionModerationAction collectionAction,
        UserModerationAction userAction,
        LocalDateTime userActionExpiresAt,
        Long adminId,
        String adminMemo,
        LocalDateTime createdAt,
        LocalDateTime processedAt
    ) {
    }

    public record CollectionInfo(
        Long collectionId,
        String title,
        String description,
        String imageUrl,
        boolean isPublic,
        CollectionModerationStatus moderationStatus,
        int bookmarkCount,
        LocalDateTime createdAt
    ) {
    }

    public record UserInfo(
        Long userId,
        String nickname,
        String profileImageUrl
    ) {
    }

    public record ContentInfo(
        Long contentId,
        String title,
        String posterUrl,
        List<String> customImageUrls,
        String reason,
        boolean isSpoiler
    ) {
    }
}
