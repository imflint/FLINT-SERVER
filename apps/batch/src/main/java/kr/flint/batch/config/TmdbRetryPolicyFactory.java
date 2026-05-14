package kr.flint.batch.config;

import org.springframework.retry.backoff.BackOffPolicy;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TmdbRetryPolicyFactory {

	private static final long DEFAULT_RETRY_BACK_OFF_MS = 2_000L;

	private final BatchProperties batchProperties;

	public BackOffPolicy fixedBackOffPolicy() {
		FixedBackOffPolicy policy = new FixedBackOffPolicy();
		policy.setBackOffPeriod(resolveRetryBackOffMs());
		return policy;
	}

	private long resolveRetryBackOffMs() {
		if (batchProperties.tmdb() == null || batchProperties.tmdb().retryBackOffMs() == null) {
			return DEFAULT_RETRY_BACK_OFF_MS;
		}

		long retryBackOffMs = batchProperties.tmdb().retryBackOffMs();
		if (retryBackOffMs < 0) {
			throw new IllegalStateException("flint.batch.tmdb.retry-back-off-ms must be greater than or equal to 0");
		}
		return retryBackOffMs;
	}
}
