package kr.flint.api.domain.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record CollectionWithUserDto(
	@Schema(type = "string")
	Long id,
	String title,
	String description,
	String image,
	Integer bookmarkCount,
	@Schema(type = "string")
	Long userId,
	String profileImageUrl,
	String nickname
) {
}
