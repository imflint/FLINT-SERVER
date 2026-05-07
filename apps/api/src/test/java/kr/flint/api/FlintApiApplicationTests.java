package kr.flint.api;

import kr.flint.api.config.AppleOAuthTestConfig;
import kr.flint.api.config.GptTestConfig;
import kr.flint.api.config.RedisTestConfig;
import kr.flint.api.config.S3TestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
})
@Import({
    S3TestConfig.class,
    RedisTestConfig.class,
    GptTestConfig.class,
    AppleOAuthTestConfig.class
})
class FlintApiApplicationTests {

    @Test
    void contextLoads() {
    }
}
