package kr.flint.infra.discord.service;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

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
public class DiscordReportNotifier {

	private static final int RED = 0xE74C3C;
	private static final String SENDER = "Flint Reporter";

	private final DiscordWebhookClient discordWebhookClient;
	private final DiscordProperties discordProperties;

	// 컬렉션 신고 알림 — webhook URL 미설정/네트워크 오류 시에도 호출처는 영향받지 않게 swallow.
	public void notifyCollectionReport(
		Long reportId,
		Long reporterId,
		Long collectionId,
		List<String> reasonLabels,
		String otherDetail
	) {
		String webhookUrl = discordProperties.webhook() == null ? null : discordProperties.webhook().report();
		if (!StringUtils.hasText(webhookUrl)) {
			log.warn("discord report webhook url not configured, skip notification (reportId={})", reportId);
			return;
		}

		try {
			DiscordEmbed.Builder builder = DiscordEmbed.builder()
				.title("🚨 컬렉션 신고")
				.color(RED)
				.timestamp(OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
				.field("신고 ID", String.valueOf(reportId), true)
				.field("신고자 ID", String.valueOf(reporterId), true)
				.field("컬렉션 ID", String.valueOf(collectionId), true)
				.field("사유", String.join(", ", reasonLabels), false);
			if (StringUtils.hasText(otherDetail)) {
				builder.field("기타 상세", otherDetail, false);
			}

			DiscordWebhookMessage message = DiscordWebhookMessage.withEmbed(SENDER, builder.build());
			discordWebhookClient.send(URI.create(webhookUrl), "application/json", message);
		} catch (Exception e) {
			// 디스코드 알림 실패는 신고 처리 흐름을 깨면 안 됨
			log.error("failed to send discord report notification (reportId={})", reportId, e);
		}
	}
}
