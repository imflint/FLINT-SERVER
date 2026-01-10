package kr.flint.infra.tmdb.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import feign.RequestInterceptor;

@Configuration
public class TmdbFeignConfig {
	@Bean
	public RequestInterceptor requestInterceptor(
		@Value("${tmdb.api-key}") String apiKey
	) {
		return requestTemplate -> {
			requestTemplate.header("Authorization", "Bearer " + apiKey);
			requestTemplate.header("accept", "application/json");
		};
	}
}
