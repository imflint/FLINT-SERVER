package kr.flint.infra.discord.client;

import java.net.URI;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import kr.flint.infra.discord.dto.DiscordWebhookMessage;

// Webhook URL은 호출 시 URI로 주입 (용도별 properties에서 가져와서 전달).
@FeignClient(name = "discordWebhookClient", url = "https://discord.com")
public interface DiscordWebhookClient {

	@PostMapping(consumes = "application/json")
	void send(
		URI webhookUrl,
		@RequestHeader("Content-Type") String contentType,
		@RequestBody DiscordWebhookMessage message
	);
}
