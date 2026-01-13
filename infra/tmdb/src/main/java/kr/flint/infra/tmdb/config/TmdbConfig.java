package kr.flint.infra.tmdb.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import kr.flint.infra.tmdb.properties.TmdbProperties;

@Configuration
@EnableConfigurationProperties(TmdbProperties.class)
public class TmdbConfig {
}
