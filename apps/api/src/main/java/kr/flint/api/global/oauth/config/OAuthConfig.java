package kr.flint.api.global.oauth.config;

import kr.flint.api.global.oauth.properties.KakaoProperties;
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
}
