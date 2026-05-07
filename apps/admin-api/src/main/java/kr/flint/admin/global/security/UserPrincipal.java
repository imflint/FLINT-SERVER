package kr.flint.admin.global.security;

import java.security.Principal;

public record UserPrincipal(
	Long userId,
	String role
) implements Principal {

	@Override
	public String getName() {
		return String.valueOf(userId);
	}
}
