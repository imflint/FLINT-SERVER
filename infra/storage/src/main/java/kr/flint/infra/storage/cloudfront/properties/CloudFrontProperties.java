package kr.flint.infra.storage.cloudfront.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cloud.aws.cloudfront")
public record CloudFrontProperties(
	String url,
	boolean enabled
) {
}
