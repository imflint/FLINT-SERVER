package kr.flint.api;

import kr.flint.api.config.S3TestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(S3TestConfig.class)
class FlintApiApplicationTests {

    @Test
    void contextLoads() {
    }
}
