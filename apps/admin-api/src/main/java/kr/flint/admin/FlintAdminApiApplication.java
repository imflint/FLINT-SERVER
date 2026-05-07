package kr.flint.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "kr.flint")
@ConfigurationPropertiesScan(basePackages = "kr.flint")
@EnableJpaRepositories(basePackages = "kr.flint")
@EntityScan(basePackages = "kr.flint")
public class FlintAdminApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(FlintAdminApiApplication.class, args);
	}
}
