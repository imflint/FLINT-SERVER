package kr.flint.collection.dto.response;

import java.time.LocalDateTime;

public interface CollectionSummaryProjection {
    Long getId();

    String getTitle();

    String getImage();

    int getBookmarkCount();

    LocalDateTime getCreatedAt();
}
