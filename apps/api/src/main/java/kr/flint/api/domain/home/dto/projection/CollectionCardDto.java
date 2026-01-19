package kr.flint.api.domain.home.dto.projection;

public record CollectionCardDto(
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
