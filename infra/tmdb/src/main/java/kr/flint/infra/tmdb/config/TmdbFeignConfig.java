package kr.flint.infra.tmdb.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import feign.RequestInterceptor;
import kr.flint.infra.tmdb.properties.TmdbProperties;
import kr.flint.infra.tmdb.client.TmdbRequestRateLimiter;

@Configuration
public class TmdbFeignConfig {

	@Bean
	public RequestInterceptor tmdbApiKeyInterceptor(
		TmdbProperties tmdbProperties,
		TmdbRequestRateLimiter rateLimiter
	) {
		return template -> {
			rateLimiter.acquire();
			template.query("api_key", tmdbProperties.apiKey());
		};
	}

	@Bean
	public TmdbRequestRateLimiter tmdbRequestRateLimiter(TmdbProperties tmdbProperties) {
		return new TmdbRequestRateLimiter(tmdbProperties.effectiveRequestsPerSecond());
	}
}
