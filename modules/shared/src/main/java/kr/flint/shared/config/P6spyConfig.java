package kr.flint.shared.config;

import com.p6spy.engine.spy.P6SpyOptions;
import jakarta.annotation.PostConstruct;
import kr.flint.shared.p6spy.CustomP6spySqlFormat;
import org.springframework.context.annotation.Configuration;

@Configuration
public class P6spyConfig {

    @PostConstruct
    public void setLogMessageFormat() {
        P6SpyOptions.getActiveInstance().setLogMessageFormat(CustomP6spySqlFormat.class.getName());
    }
}
