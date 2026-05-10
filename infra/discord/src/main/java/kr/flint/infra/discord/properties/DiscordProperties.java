package kr.flint.infra.discord.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "discord")
public record DiscordProperties(
	Webhook webhook
) {
	// webhook은 용도별로 키를 분리해 다른 알림(공지/오류 등)이 추가될 때 충돌하지 않도록 한다.
	public record Webhook(
		// 컬렉션 신고 알림 전용 webhook URL
		String report
	) {
	}
}
