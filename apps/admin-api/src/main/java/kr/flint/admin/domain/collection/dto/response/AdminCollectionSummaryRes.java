package kr.flint.admin.domain.collection.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.flint.collection.domain.CollectionModerationStatus;

@Schema(description = "관리자 컬렉션 목록 응답")
public record AdminCollectionSummaryRes(
    Long collectionId,
    String title,
    String description,
    String imageUrl,
    boolean isPublic,
    CollectionModerationStatus moderationStatus,
    int bookmarkCount,
    Long ownerId,
    String ownerNickname,
    int contentCount,
    LocalDateTime createdAt
) {
}
