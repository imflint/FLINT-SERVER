package kr.flint.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "oauth.kakao")
public record KakaoProperties(
        String clientId,
        String clientSecret,
        String redirectUri,
        String tokenUrl,
        String userInfoUrl,
        Duration connectTimeout,
        Duration readTimeout
) {
}
