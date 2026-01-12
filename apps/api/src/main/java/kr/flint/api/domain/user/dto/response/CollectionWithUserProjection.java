package kr.flint.api.domain.user.dto.response;

public interface CollectionWithUserProjection {
    Long getId();

    String getTitle();

    String getImage();

    String getProfileImage();

    String getUserName();
}
