package kr.flint.api.domain.search.repository;

import kr.flint.api.domain.search.dto.response.BookmarkedContentSearchRes;

public interface BookmarkedContentSearchProjection {
    Long getBookmarkId();
    Long getContentId();
    String getTitle();
    String getAuthor();
    String getPosterUrl();
    int getYear();

    default BookmarkedContentSearchRes toResponse() {
        return new BookmarkedContentSearchRes(getBookmarkId(), getContentId(), getTitle(), getAuthor(),
            getPosterUrl(), getYear());
    }
}
