package kr.flint.infra.storage.cloudfront;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import kr.flint.infra.storage.cloudfront.properties.CloudFrontProperties;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(CloudFrontProperties.class)
public class CloudFrontUrlProvider {

	private final CloudFrontProperties cloudFrontProperties;

	public String resolveUrl(String key) {
		if (!StringUtils.hasText(key)) {
			return null;
		}
		// 이미 완전한 URL인 경우 그대로 반환 (TMDB 등 외부 이미지)
		if (key.startsWith("http://") || key.startsWith("https://")) {
			return key;
		}
		if (!cloudFrontProperties.enabled()) {
			return key;
		}
		return buildCloudFrontUrl(key);
	}

	private String buildCloudFrontUrl(String key) {
		String baseUrl = cloudFrontProperties.url();
		if (!baseUrl.startsWith("https://") && !baseUrl.startsWith("http://")) {
			baseUrl = "https://" + baseUrl;
		}
		String normalizedUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		String normalizedKey = key.startsWith("/") ? key.substring(1) : key;
		return normalizedUrl + "/" + normalizedKey;
	}
}
