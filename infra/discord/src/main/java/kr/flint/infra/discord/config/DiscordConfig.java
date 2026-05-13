package kr.flint.infra.discord.config;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import kr.flint.infra.discord.properties.DiscordProperties;

@Configuration
@EnableConfigurationProperties(DiscordProperties.class)
public class DiscordConfig {

	@Bean
	@ConditionalOnMissingBean(Clock.class)
	Clock clock() {
		return Clock.systemUTC();
	}
}
