package kr.flint.admin.domain.auth.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "flint.admin.auth")
public record AdminAuthProperties(
    String username,
    String passwordHash
) {

    public boolean configured() {
        return StringUtils.hasText(username)
            && StringUtils.hasText(passwordHash);
    }
}
