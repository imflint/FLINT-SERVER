package kr.flint.collection.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import kr.flint.shared.domain.BaseTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Collection extends BaseTime {

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String description;

    @Column(name = "collection_image", nullable = true)
    private String image;

    @Column(nullable = false)
    private boolean isPublic;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private int bookmarkCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CollectionModerationStatus moderationStatus;

    private LocalDateTime deletedAt;

    public static Collection create(String title, String description, String image, boolean isPublic, Long userId) {
        return Collection.builder()
            .title(title)
            .description(description)
            .image(image)
            .isPublic(isPublic)
            .userId(userId)
            .bookmarkCount(0)
            .moderationStatus(CollectionModerationStatus.VISIBLE)
            .build();
    }

    public void update(String title, String description, String image, boolean isPublic) {
        this.title = title;
        this.description = description;
        this.image = image;
        this.isPublic = isPublic;
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId != null && this.userId.equals(userId);
    }

    public void hideByAdmin() {
        this.moderationStatus = CollectionModerationStatus.HIDDEN;
    }

    public void delete(LocalDateTime deletedAt) {
        if (this.deletedAt == null) {
            this.deletedAt = deletedAt;
        }
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    // TODO: 동시성 이슈 및 sync 체크
    public void increaseBookmarkCount() {
        this.bookmarkCount++;
    }

    public void decreaseBookmarkCount() {
        if (this.bookmarkCount > 0) {
            this.bookmarkCount--;
        }
    }
}
