package kr.flint.api.domain.user.dto.response;

public interface CollectionWithUserProjection {
    Long getId();

    String getTitle();

    String getDescription();

    String getImage();

    Integer getBookmarkCount();

    Long getUserId();

    String getProfileImage();

    String getNickname();
}
