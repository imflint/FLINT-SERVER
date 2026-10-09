package kr.flint.api.domain.content.repository;

import kr.flint.api.domain.content.repository.ContentQueryRepository.ContentSearchRow;

public interface ContentSearchProjection {
    Long getId();
    String getTitle();
    String getAuthor();
    String getPosterUrl();
    int getYear();
    int getBookmarkCount();
    int getExactMatchRank();
    double getRelevanceScore();

    default ContentSearchRow toSearchRow() {
        return new ContentSearchRow(getId(), getTitle(), getAuthor(), getPosterUrl(), getYear(),
            getBookmarkCount(), getExactMatchRank(), getRelevanceScore());
    }
}
