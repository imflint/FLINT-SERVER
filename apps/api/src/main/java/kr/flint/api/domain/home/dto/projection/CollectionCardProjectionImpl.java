package kr.flint.api.domain.home.dto.projection;

public record CollectionCardProjectionImpl(
    Long id,
    String title,
    String image,
    String profileImage,
    String userName
) implements CollectionCardProjection {

    @Override
    public Long getId() { return id; }

    @Override
    public String getTitle() { return title; }

    @Override
    public String getImage() { return image; }

    @Override
    public String getProfileImage() { return profileImage; }

    @Override
    public String getUserName() { return userName; }
}
