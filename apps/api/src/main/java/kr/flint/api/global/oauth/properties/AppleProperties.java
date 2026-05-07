package kr.flint.api.global.oauth.properties;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "oauth.apple")
public record AppleProperties(
		String clientId,
		String jwksUrl,
		Duration connectTimeout,
		Duration readTimeout,
		Duration jwksCacheTtl
) {
}
