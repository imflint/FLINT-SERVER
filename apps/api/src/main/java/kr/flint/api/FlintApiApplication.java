package kr.flint.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "kr.flint")
public class FlintApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlintApiApplication.class, args);
    }
}
