package kr.flint.batch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "flint.batch")
public record BatchProperties(
	Scheduling scheduling,
	Tmdb tmdb
) {
	public BatchProperties {
		scheduling = scheduling == null ? new Scheduling(false) : scheduling;
		tmdb = tmdb == null ? Tmdb.defaults() : tmdb;
	}

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
		public Tmdb {
			exportBaseUrl = defaultIfBlank(exportBaseUrl, "https://files.tmdb.org/p/exports");
			downloadDir = defaultIfBlank(
				downloadDir,
				System.getProperty("java.io.tmpdir") + "/flint-tmdb-exports"
			);
			chunkSize = chunkSize == null ? 50 : chunkSize;
			concurrencyLimit = concurrencyLimit == null ? 3 : concurrencyLimit;
			retryAttempts = retryAttempts == null ? 3 : retryAttempts;
			retryBackOffMs = retryBackOffMs == null ? 2_000L : retryBackOffMs;
		}

		private static Tmdb defaults() {
			return new Tmdb(null, null, null, null, null, null);
		}

		private static String defaultIfBlank(String value, String fallback) {
			return value == null || value.isBlank() ? fallback : value;
		}
	}
}
