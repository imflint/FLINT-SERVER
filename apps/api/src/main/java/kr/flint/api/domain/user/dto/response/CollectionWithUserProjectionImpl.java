package kr.flint.api.domain.user.dto.response;

public record CollectionWithUserProjectionImpl(
	Long id,
	String title,
	String image,
	String profileImage,
	String userName
) implements CollectionWithUserProjection {

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
