package kr.flint.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        SecurityScheme bearerScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .in(SecurityScheme.In.HEADER)
                .name("Authorization");

        SecurityRequirement securityRequirement = new SecurityRequirement()
                .addList("bearerAuth");

        return new OpenAPI()
                .info(new Info()
                        .title("Flint API")
                        .description("Flint API 명세")
                        .version("v1"))
                .servers(List.of(
                        new Server().url("/").description("Current Server")
                ))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", bearerScheme))
                .addSecurityItem(securityRequirement);
    }

    private GroupedOpenApi buildGroupedOpenApi(String group, String basePackage) {
        return GroupedOpenApi.builder()
                .group(group)
                .pathsToMatch("/api/v1/**")
                .packagesToScan(basePackage)
                .build();
    }
}