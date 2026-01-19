package kr.flint.api.domain.user.dto.response;

public record CollectionWithUserDto(
	Long id,
	String title,
	String description,
	String image,
	Integer bookmarkCount,
	Long userId,
	String profileImage,
	String nickname
) {
}
