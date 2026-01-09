package kr.flint.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, KakaoProperties.class})
public class AuthConfig {

    @Bean
    public RestClient kakaoRestClient() {
        return RestClient.builder()
                .build();
    }
}
