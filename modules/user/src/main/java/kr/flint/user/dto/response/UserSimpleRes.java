package kr.flint.user.dto.response;

import kr.flint.user.domain.User;

public record UserSimpleRes(
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
