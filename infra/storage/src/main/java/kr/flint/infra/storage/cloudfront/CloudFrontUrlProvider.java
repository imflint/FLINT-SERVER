package kr.flint.infra.storage.cloudfront;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import kr.flint.infra.storage.cloudfront.properties.CloudFrontProperties;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(CloudFrontProperties.class)
@ConditionalOnProperty(prefix = "cloud.aws.cloudfront", name = "enabled", havingValue = "true")
public class CloudFrontUrlProvider {

	private final CloudFrontProperties cloudFrontProperties;

	public String getUrl(String key) {
		if (!StringUtils.hasText(key)) {
			return null;
		}
		String baseUrl = cloudFrontProperties.url();
		String normalizedUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		String normalizedKey = key.startsWith("/") ? key.substring(1) : key;
		return normalizedUrl + "/" + normalizedKey;
	}
}
