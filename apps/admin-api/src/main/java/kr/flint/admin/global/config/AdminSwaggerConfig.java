package kr.flint.admin.global.config;

import java.util.List;
import java.util.Map;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

@Configuration
public class AdminSwaggerConfig {

	@Bean
	public ModelResolver modelResolver(ObjectMapper objectMapper) {
		return new ModelResolver(objectMapper);
	}

	@Bean
	public OpenApiCustomizer longToStringSchemaCustomizer() {
		return openApi -> {
			if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
				return;
			}
			openApi.getComponents().getSchemas().values().forEach(this::convertLongToString);
		};
	}

	@SuppressWarnings("rawtypes")
	private void convertLongToString(Schema<?> schema) {
		if (schema == null) {
			return;
		}

		Map<String, Schema> properties = schema.getProperties();
		if (properties != null) {
			properties.forEach((name, propSchema) -> {
				if (isLongType(propSchema)) {
					propSchema.setType("string");
					propSchema.setFormat(null);
				}
				convertLongToString(propSchema);
			});
		}

		if (schema.getItems() != null) {
			convertLongToString(schema.getItems());
		}
	}

	@SuppressWarnings("rawtypes")
	private boolean isLongType(Schema schema) {
		return "integer".equals(schema.getType()) && "int64".equals(schema.getFormat());
	}

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
				.title("Flint Admin API")
				.description("Flint Admin API 명세")
				.version("v1"))
			.servers(List.of(new Server().url("/").description("Current Server")))
			.components(new Components().addSecuritySchemes("bearerAuth", bearerScheme))
			.addSecurityItem(securityRequirement);
	}
}
