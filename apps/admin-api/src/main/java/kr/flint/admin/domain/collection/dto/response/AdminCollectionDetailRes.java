package kr.flint.admin.domain.collection.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.flint.collection.domain.CollectionModerationStatus;
import kr.flint.content.domain.MediaType;

@Schema(description = "관리자 컬렉션 상세 응답")
public record AdminCollectionDetailRes(
    Long collectionId,
    String title,
    String description,
    String imageUrl,
    boolean isPublic,
    CollectionModerationStatus moderationStatus,
    int bookmarkCount,
    LocalDateTime createdAt,
    OwnerInfo owner,
    List<ContentInfo> contents
) {
    public record OwnerInfo(
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
        boolean isSpoiler,
        String reason,
        int year,
        MediaType mediaType
    ) {
    }
}
