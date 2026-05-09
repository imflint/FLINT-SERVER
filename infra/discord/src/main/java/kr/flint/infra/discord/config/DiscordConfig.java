package kr.flint.infra.discord.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import kr.flint.infra.discord.properties.DiscordProperties;

@Configuration
@EnableConfigurationProperties(DiscordProperties.class)
public class DiscordConfig {
}
