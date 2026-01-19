package kr.flint.api.domain.user.dto.response;

public record CollectionWithUserProjectionImpl(
	Long id,
	String title,
	String description,
	String image,
	Integer bookmarkCount,
	Long userId,
	String profileImage,
	String nickname
) implements CollectionWithUserProjection {

	@Override
	public Long getId() { return id; }

	@Override
	public String getTitle() { return title; }

	@Override
	public String getDescription() { return description; }

	@Override
	public String getImage() { return image; }

	@Override
	public Integer getBookmarkCount() { return bookmarkCount; }

	@Override
	public Long getUserId() { return userId; }

	@Override
	public String getProfileImage() { return profileImage; }

	@Override
	public String getNickname() { return nickname; }
}
