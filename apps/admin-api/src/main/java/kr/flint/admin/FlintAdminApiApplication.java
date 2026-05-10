package kr.flint.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import kr.flint.batch.FlintBatchApplication;
import kr.flint.batch.config.BatchSecurityConfig;

@SpringBootApplication
@ComponentScan(
	basePackages = "kr.flint",
	excludeFilters = @ComponentScan.Filter(
		type = FilterType.ASSIGNABLE_TYPE,
		classes = {FlintBatchApplication.class, BatchSecurityConfig.class}
	)
)
@ConfigurationPropertiesScan(basePackages = "kr.flint")
@EnableJpaRepositories(basePackages = "kr.flint")
@EntityScan(basePackages = "kr.flint")
@EnableFeignClients(basePackages = "kr.flint.infra.tmdb")
@EnableAsync
@EnableScheduling
public class FlintAdminApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(FlintAdminApiApplication.class, args);
	}
}
