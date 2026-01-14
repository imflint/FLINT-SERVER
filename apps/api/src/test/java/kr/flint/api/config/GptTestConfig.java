package kr.flint.api.config;

import kr.flint.infra.gpt.service.ChatService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.mockito.Mockito.mock;

@TestConfiguration
public class GptTestConfig {

    @Bean
    @Primary
    public ChatService chatService() {
        return mock(ChatService.class);
    }
}
