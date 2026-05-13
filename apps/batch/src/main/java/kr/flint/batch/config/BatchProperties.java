package kr.flint.batch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "flint.batch")
public record BatchProperties(
	Scheduling scheduling,
	Tmdb tmdb
) {
	public record Scheduling(boolean enabled) {
	}

	public record Tmdb(
		String exportBaseUrl,
		String downloadDir,
		Integer chunkSize,
		Integer concurrencyLimit,
		Integer retryAttempts,
		Long retryBackOffMs
	) {
	}
}
