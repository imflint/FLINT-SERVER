package kr.flint.api.config;

import java.time.Duration;

import kr.flint.api.global.oauth.client.AppleOAuthClient;
import kr.flint.api.global.oauth.properties.AppleProperties;
import kr.flint.auth.jwt.AppleIdentityTokenVerifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.mockito.Mockito.mock;

@TestConfiguration
public class AppleOAuthTestConfig {

    @Bean
    @Primary
    public AppleProperties testAppleProperties() {
        return new AppleProperties(
                "test.bundle.id",
                "https://appleid.apple.com/auth/keys",
                Duration.ofSeconds(5),
                Duration.ofSeconds(5),
                Duration.ofHours(24)
        );
    }

    @Bean
    @Primary
    public AppleIdentityTokenVerifier testAppleIdentityTokenVerifier() {
        return mock(AppleIdentityTokenVerifier.class);
    }

    @Bean
    @Primary
    public AppleOAuthClient testAppleOAuthClient() {
        return mock(AppleOAuthClient.class);
    }
}
