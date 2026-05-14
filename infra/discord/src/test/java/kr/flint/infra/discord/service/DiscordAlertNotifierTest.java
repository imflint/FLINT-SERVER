package kr.flint.infra.discord.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import kr.flint.infra.discord.client.DiscordWebhookClient;
import kr.flint.infra.discord.dto.DiscordEmbed;
import kr.flint.infra.discord.dto.DiscordWebhookMessage;
import kr.flint.infra.discord.properties.DiscordProperties;

@ExtendWith(MockitoExtension.class)
class DiscordAlertNotifierTest {

	private static final String WEBHOOK_URL = "https://discord.com/api/webhooks/test";

	@Mock
	private DiscordWebhookClient discordWebhookClient;

	private MutableClock clock;
	private DiscordAlertNotifier notifier;

	@BeforeEach
	void setUp() {
		clock = new MutableClock(Instant.parse("2026-05-13T00:00:00Z"));
		notifier = new DiscordAlertNotifier(discordWebhookClient, properties(300, 5), clock);
	}

	@Nested
	@DisplayName("notifyServerError")
	class NotifyServerError {

		@Test
		@DisplayName("같은 오류는 5분 동안 최대 5건만 전송")
		void sameFingerprintMaxFiveAlertsInWindow() {
			for (int i = 0; i < 6; i++) {
				notifySameError();
			}

			verify(discordWebhookClient, times(5))
				.send(any(URI.class), eq("application/json"), any(DiscordWebhookMessage.class));
		}

		@Test
		@DisplayName("제한 구간이 지나면 같은 오류를 다시 전송")
		void allowAgainAfterWindow() {
			for (int i = 0; i < 6; i++) {
				notifySameError();
			}

			clock.advance(Duration.ofSeconds(301));
			notifySameError();

			ArgumentCaptor<DiscordWebhookMessage> captor = ArgumentCaptor.forClass(DiscordWebhookMessage.class);
			verify(discordWebhookClient, times(6))
				.send(any(URI.class), eq("application/json"), captor.capture());

			DiscordEmbed lastEmbed = captor.getAllValues().getLast().embeds().getFirst();
			assertThat(fieldValue(lastEmbed, "최근 제한 구간 억제된 동일 오류")).isEqualTo("1건");
			assertThat(fieldValue(lastEmbed, "최근 제한 구간 전송 순번")).isEqualTo("1/5");
		}

		@Test
		@DisplayName("다른 fingerprint는 독립적으로 제한")
		void differentFingerprintHasOwnLimit() {
			for (int i = 0; i < 5; i++) {
				notifySameError();
			}

			notifier.notifyServerError(
				"api",
				"POST",
				"/api/v1/collections",
				null,
				"127.0.0.1",
				new IllegalStateException("same class different uri")
			);

			verify(discordWebhookClient, times(6))
				.send(any(URI.class), eq("application/json"), any(DiscordWebhookMessage.class));
		}

		@Test
		@DisplayName("webhook URL이 없으면 알림을 전송하지 않음")
		void missingWebhookUrl() {
			DiscordAlertNotifier noWebhookNotifier = new DiscordAlertNotifier(
				discordWebhookClient,
				new DiscordProperties(
					new DiscordProperties.Webhook(null, null),
					new DiscordProperties.Alert(true, null, new DiscordProperties.RateLimit(300L, 5, 1000, 300L))
				),
				clock
			);

			noWebhookNotifier.notifyServerError(
				"api",
				"GET",
				"/api/v1/users/1",
				null,
				"127.0.0.1",
				new IllegalStateException("error")
			);

			verify(discordWebhookClient, never())
				.send(any(URI.class), eq("application/json"), any(DiscordWebhookMessage.class));
		}
	}

	private void notifySameError() {
		notifier.notifyServerError(
			"api",
			"GET",
			"/api/v1/users/1",
			"cursor=1",
			"127.0.0.1",
			new IllegalStateException("test error")
		);
	}

	private DiscordProperties properties(long windowSeconds, int maxAlerts) {
		return new DiscordProperties(
			new DiscordProperties.Webhook(null, WEBHOOK_URL),
			new DiscordProperties.Alert(
				true,
				null,
				new DiscordProperties.RateLimit(windowSeconds, maxAlerts, 1000, 300L)
			)
		);
	}

	private String fieldValue(DiscordEmbed embed, String name) {
		return embed.fields().stream()
			.filter(field -> name.equals(field.name()))
			.map(DiscordEmbed.Field::value)
			.findFirst()
			.orElseThrow();
	}

	private static final class MutableClock extends Clock {
		private Instant instant;

		private MutableClock(Instant instant) {
			this.instant = instant;
		}

		@Override
		public ZoneId getZone() {
			return ZoneId.of("UTC");
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return instant;
		}

		private void advance(Duration duration) {
			instant = instant.plus(duration);
		}
	}
}
