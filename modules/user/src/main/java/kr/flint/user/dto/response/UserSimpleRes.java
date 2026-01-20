package kr.flint.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.flint.user.domain.User;

public record UserSimpleRes(
	@Schema(type = "string")
	Long userId,
	String nickName,
	String profileImageUrl,
	String userRole
) {
	public static UserSimpleRes from(User user) {
		return new UserSimpleRes(
			user.getId(),
			user.getNickname(),
			user.getProfileImage(),
			user.getUserRole().toString()
		);
	}
}
