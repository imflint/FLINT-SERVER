package kr.flint.infra.tmdb.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tmdb")
public record TmdbProperties(
	String baseUrl,
	String apiKey,
	Integer requestsPerSecond
) {
	public int effectiveRequestsPerSecond() {
		return requestsPerSecond == null ? 5 : requestsPerSecond;
	}
}
