package kr.flint.api.global.oauth.config;

import kr.flint.api.global.oauth.properties.AppleProperties;
import kr.flint.api.global.oauth.properties.KakaoProperties;
import kr.flint.auth.jwt.AppleIdentityTokenVerifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class OAuthConfig {

    @Bean
    public RestClient kakaoRestClient(KakaoProperties kakaoProperties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(kakaoProperties.connectTimeout());
        factory.setReadTimeout(kakaoProperties.readTimeout());

        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    @Bean
    public RestClient appleRestClient(AppleProperties appleProperties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(appleProperties.connectTimeout());
        factory.setReadTimeout(appleProperties.readTimeout());

        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    @Bean
    public AppleIdentityTokenVerifier appleIdentityTokenVerifier(
            AppleProperties appleProperties, RestClient appleRestClient) {
        return new AppleIdentityTokenVerifier(
                appleProperties.clientId(),
                appleProperties.jwksUrl(),
                appleProperties.jwksCacheTtl(),
                appleRestClient
        );
    }
}
