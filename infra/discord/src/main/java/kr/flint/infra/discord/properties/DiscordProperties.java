package kr.flint.infra.discord.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "discord")
public record DiscordProperties(
	Webhook webhook,
	Alert alert
) {
	// webhook은 용도별로 키를 분리해 다른 알림(공지/오류 등)이 추가될 때 충돌하지 않도록 한다.
	public record Webhook(
		// 컬렉션 신고 알림 전용 webhook URL
		String report,
		// 애플리케이션 내부 서버 오류 알림 전용 webhook URL
		String alert
	) {
	}

	public record Alert(
		Boolean enabled,
		Long deduplicateSeconds,
		RateLimit rateLimit
	) {
		public boolean isEnabled() {
			return enabled == null || enabled;
		}

		public long rateLimitWindowSecondsOrDefault() {
			if (rateLimit != null && rateLimit.windowSeconds() != null) {
				return Math.max(1L, rateLimit.windowSeconds());
			}
			return deduplicateSeconds == null ? 300L : Math.max(1L, deduplicateSeconds);
		}

		public int rateLimitMaxAlertsOrDefault() {
			return rateLimit == null || rateLimit.maxAlerts() == null
				? 5
				: Math.max(1, rateLimit.maxAlerts());
		}

		public int rateLimitMaxFingerprintsOrDefault() {
			return rateLimit == null || rateLimit.maxFingerprints() == null
				? 1000
				: Math.max(1, rateLimit.maxFingerprints());
		}

		public long rateLimitCleanupIntervalSecondsOrDefault() {
			return rateLimit == null || rateLimit.cleanupIntervalSeconds() == null
				? 300L
				: Math.max(1L, rateLimit.cleanupIntervalSeconds());
		}
	}

	public record RateLimit(
		Long windowSeconds,
		Integer maxAlerts,
		Integer maxFingerprints,
		Long cleanupIntervalSeconds
	) {
	}
}
