package kr.flint.api.global.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import kr.flint.api.domain.content.repository.ContentWordSearchSchemaRepository;

@Configuration
@ConditionalOnProperty(name = "flint.content.english-word-search-enabled", havingValue = "true")
public class EnglishWordSearchConfig {
    @Bean
    public InitializingBean englishWordSearchPreflight(ContentWordSearchSchemaRepository repository) {
        return repository::validate;
    }
}
