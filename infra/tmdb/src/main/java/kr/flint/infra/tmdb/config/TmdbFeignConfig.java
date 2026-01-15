package kr.flint.infra.tmdb.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import feign.RequestInterceptor;
import kr.flint.infra.tmdb.properties.TmdbProperties;

@Configuration
public class TmdbFeignConfig {

	@Bean
	public RequestInterceptor tmdbApiKeyInterceptor(TmdbProperties tmdbProperties) {
		return template -> template.query("api_key", tmdbProperties.apiKey());
	}
}
