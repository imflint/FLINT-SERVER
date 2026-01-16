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

	/**
	 * CloudFront URL 생성
	 * @param key S3 객체 키 (예: "images/photo.jpg")
	 * @return CloudFront URL
	 */
	public String getUrl(String key) {
		if (!StringUtils.hasText(key)) {
			return null;
		}
		return cloudFrontProperties.url() + "/" + key;
	}
}
