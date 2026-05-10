package kr.flint.infra.discord.dto;

import java.util.List;

public record DiscordWebhookMessage(
	String username,
	String content,
	List<DiscordEmbed> embeds
) {
	public static DiscordWebhookMessage withEmbed(String username, DiscordEmbed embed) {
		return new DiscordWebhookMessage(username, null, List.of(embed));
	}
}
