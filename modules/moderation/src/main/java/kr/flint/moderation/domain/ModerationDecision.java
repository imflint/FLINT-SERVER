package kr.flint.moderation.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import kr.flint.shared.domain.BaseTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "moderation_decisions")
@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ModerationDecision extends BaseTime {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ModerationSourceType sourceType;

    @Column(nullable = false)
    private Long sourceId;

    @Column(nullable = false)
    private Long collectionId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long adminUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CollectionModerationAction collectionAction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UserModerationAction userAction;

    private LocalDateTime userActionExpiresAt;

    @Column(length = 500)
    private String adminMemo;

    public static ModerationDecision createCollectionReportDecision(
        Long reportId,
        Long collectionId,
        Long userId,
        Long adminUserId,
        CollectionModerationAction collectionAction,
        UserModerationAction userAction,
        LocalDateTime userActionExpiresAt,
        String adminMemo
    ) {
        return ModerationDecision.builder()
            .sourceType(ModerationSourceType.COLLECTION_REPORT)
            .sourceId(reportId)
            .collectionId(collectionId)
            .userId(userId)
            .adminUserId(adminUserId)
            .collectionAction(collectionAction)
            .userAction(userAction)
            .userActionExpiresAt(userActionExpiresAt)
            .adminMemo(adminMemo)
            .build();
    }
}
