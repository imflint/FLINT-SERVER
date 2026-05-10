package kr.flint.admin.domain.auth.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "flint.admin.auth")
public record AdminAuthProperties(
	Long userId,
	String username,
	String passwordHash
) {

	public boolean configured() {
		return userId != null
			&& StringUtils.hasText(username)
			&& StringUtils.hasText(passwordHash);
	}
}
