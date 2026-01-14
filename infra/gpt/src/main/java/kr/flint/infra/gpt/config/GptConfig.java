package kr.flint.infra.gpt.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GptConfig {
	@Bean
	ChatClient chatClient(ChatClient.Builder builder) {
		return builder.build();
	}
}
