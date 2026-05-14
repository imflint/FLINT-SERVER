package kr.flint.infra.discord.service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import kr.flint.infra.discord.client.DiscordWebhookClient;
import kr.flint.infra.discord.dto.DiscordEmbed;
import kr.flint.infra.discord.dto.DiscordWebhookMessage;
import kr.flint.infra.discord.properties.DiscordProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiscordAlertNotifier {

	private static final int RED = 0xE74C3C;
	private static final String SENDER = "Flint Alerts";
	private static final int MAX_MESSAGE_LENGTH = 1000;
	private static final int MAX_STACK_TRACE_LENGTH = 3000;

	private final DiscordWebhookClient discordWebhookClient;
	private final DiscordProperties discordProperties;
	private final Clock clock;
	private final ConcurrentMap<String, AlertWindow> alertWindows = new ConcurrentHashMap<>();
	private volatile Instant lastCleanupAt = Instant.EPOCH;

	@Async
	public void notifyServerError(
		String serviceName,
		String method,
		String uri,
		String queryString,
		String clientIp,
		Throwable exception
	) {
		if (!isEnabled()) {
			return;
		}

		String webhookUrl = discordProperties.webhook() == null ? null : discordProperties.webhook().alert();
		if (!StringUtils.hasText(webhookUrl)) {
			log.debug("디스코드 서버 오류 알림 webhook URL이 설정되지 않아 알림을 건너뜁니다.");
			return;
		}

		String fingerprint = createFingerprint(serviceName, method, uri, exception);
		AlertDecision decision = reserveAlert(fingerprint);
		if (!decision.allowed()) {
			log.debug("디스코드 서버 오류 알림 중복 제한으로 건너뜁니다. fingerprint={}", fingerprint);
			return;
		}

		try {
			DiscordEmbed embed = DiscordEmbed.builder()
				.title("애플리케이션 서버 오류")
				.description(truncate(stackTrace(exception), MAX_STACK_TRACE_LENGTH))
				.color(RED)
				.timestamp(OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC)
					.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
				.field("서비스", serviceName, true)
				.field("요청", method + " " + uriWithQuery(uri, queryString), false)
				.field("클라이언트 IP", nullToDash(clientIp), true)
				.field("예외", exception.getClass().getName(), false)
				.field("메시지", truncate(nullToDash(exception.getMessage()), MAX_MESSAGE_LENGTH), false)
				.field("fingerprint", fingerprint, false)
				.field("최근 제한 구간 전송 순번", decision.sequence() + "/" + decision.maxAlerts(), true)
				.field("최근 제한 구간 억제된 동일 오류", decision.suppressedCount() + "건", true)
				.build();

			discordWebhookClient.send(
				URI.create(webhookUrl),
				"application/json",
				DiscordWebhookMessage.withEmbed(SENDER, embed)
			);
		} catch (Exception e) {
			log.error("디스코드 서버 오류 알림 전송 실패. fingerprint={}", fingerprint, e);
		}
	}

	private boolean isEnabled() {
		return discordProperties.alert() == null || discordProperties.alert().isEnabled();
	}

	private AlertDecision reserveAlert(String fingerprint) {
		Instant now = clock.instant();
		Duration window = Duration.ofSeconds(rateLimitWindowSeconds());
		int maxAlerts = rateLimitMaxAlerts();
		AtomicReference<AlertDecision> decisionRef = new AtomicReference<>(AlertDecision.suppressed());

		alertWindows.compute(fingerprint, (key, current) -> {
			AlertWindow alertWindow = current == null ? new AlertWindow() : current;
			alertWindow.removeExpired(now, window);

			if (alertWindow.sentCount() >= maxAlerts) {
				alertWindow.suppress(now);
				decisionRef.set(AlertDecision.suppressed());
				return alertWindow;
			}

			long suppressedCount = alertWindow.consumeSuppressedCount();
			alertWindow.markSent(now);
			decisionRef.set(AlertDecision.allowed(alertWindow.sentCount(), maxAlerts, suppressedCount));
			return alertWindow;
		});

		cleanupExpiredWindows(now, window);
		return decisionRef.get();
	}

	private long rateLimitWindowSeconds() {
		return discordProperties.alert() == null
			? 300L
			: discordProperties.alert().rateLimitWindowSecondsOrDefault();
	}

	private int rateLimitMaxAlerts() {
		return discordProperties.alert() == null
			? 5
			: discordProperties.alert().rateLimitMaxAlertsOrDefault();
	}

	private int rateLimitMaxFingerprints() {
		return discordProperties.alert() == null
			? 1000
			: discordProperties.alert().rateLimitMaxFingerprintsOrDefault();
	}

	private long rateLimitCleanupIntervalSeconds() {
		return discordProperties.alert() == null
			? 300L
			: discordProperties.alert().rateLimitCleanupIntervalSecondsOrDefault();
	}

	private void cleanupExpiredWindows(Instant now, Duration window) {
		if (alertWindows.size() <= rateLimitMaxFingerprints()
			&& Duration.between(lastCleanupAt, now).getSeconds() < rateLimitCleanupIntervalSeconds()) {
			return;
		}

		lastCleanupAt = now;
		Duration ttl = window.multipliedBy(2);
		for (String fingerprint : alertWindows.keySet()) {
			alertWindows.computeIfPresent(fingerprint, (key, alertWindow) ->
				alertWindow.isExpired(now, ttl) ? null : alertWindow
			);
		}
		trimOldestWindows();
	}

	private void trimOldestWindows() {
		int maxFingerprints = rateLimitMaxFingerprints();
		int excess = alertWindows.size() - maxFingerprints;
		if (excess <= 0) {
			return;
		}

		int removed = 0;
		for (String fingerprint : alertWindows.keySet()) {
			if (removed >= excess) {
				return;
			}
			if (alertWindows.remove(fingerprint) != null) {
				removed++;
			}
		}
	}

	private String createFingerprint(String serviceName, String method, String uri, Throwable exception) {
		return serviceName + ":" + method + ":" + uri + ":" + exception.getClass().getName();
	}

	private String uriWithQuery(String uri, String queryString) {
		if (!StringUtils.hasText(queryString)) {
			return uri;
		}
		return uri + "?" + queryString;
	}

	private String stackTrace(Throwable exception) {
		StringWriter writer = new StringWriter();
		exception.printStackTrace(new PrintWriter(writer));
		return writer.toString();
	}

	private String truncate(String value, int maxLength) {
		if (value == null || value.length() <= maxLength) {
			return value;
		}
		return value.substring(0, maxLength - 3) + "...";
	}

	private String nullToDash(String value) {
		return StringUtils.hasText(value) ? value : "-";
	}

	private static final class AlertWindow {
		private final Deque<Instant> sentAt = new ArrayDeque<>();
		private Instant lastTouchedAt = Instant.EPOCH;
		private long suppressedCount;

		private void removeExpired(Instant now, Duration window) {
			while (!sentAt.isEmpty() && Duration.between(sentAt.peekFirst(), now).compareTo(window) >= 0) {
				sentAt.removeFirst();
			}
		}

		private int sentCount() {
			return sentAt.size();
		}

		private void markSent(Instant now) {
			sentAt.addLast(now);
			lastTouchedAt = now;
		}

		private void suppress(Instant now) {
			suppressedCount++;
			lastTouchedAt = now;
		}

		private long consumeSuppressedCount() {
			long count = suppressedCount;
			suppressedCount = 0;
			return count;
		}

		private boolean isExpired(Instant now, Duration ttl) {
			return Duration.between(lastTouchedAt, now).compareTo(ttl) >= 0;
		}

	}

	private record AlertDecision(
		boolean allowed,
		int sequence,
		int maxAlerts,
		long suppressedCount
	) {
		private static AlertDecision allowed(int sequence, int maxAlerts, long suppressedCount) {
			return new AlertDecision(true, sequence, maxAlerts, suppressedCount);
		}

		private static AlertDecision suppressed() {
			return new AlertDecision(false, 0, 0, 0);
		}
	}
}
