package kr.flint.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "kr.flint")
@EnableJpaRepositories(basePackages = "kr.flint")
@EntityScan(basePackages = "kr.flint")
public class FlintApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlintApiApplication.class, args);
    }
}
