package kr.flint.admin.global.config;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
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
			if (openApi.getComponents() != null && openApi.getComponents().getSchemas() != null) {
				openApi.getComponents().getSchemas().values().forEach(this::convertLongToString);
			}
			if (openApi.getPaths() != null) {
				openApi.getPaths().values().forEach(pathItem ->
					pathItem.readOperations().forEach(this::convertOperationLongToString)
				);
			}
		};
	}

	@Bean
	public OpenApiCustomizer adminPublicEndpointsCustomizer() {
		return openApi -> {
			if (openApi.getPaths() == null) {
				return;
			}

			PathItem loginPath = openApi.getPaths().get("/api/v1/admin/auth/login");
			if (loginPath != null && loginPath.getPost() != null) {
				loginPath.getPost().setSecurity(List.of());
			}
		};
	}

	@SuppressWarnings("rawtypes")
	private void convertLongToString(Schema<?> schema) {
		if (schema == null) {
			return;
		}

		if (isLongType(schema)) {
			schema.setType("string");
			schema.setTypes(Set.of("string"));
			schema.setFormat(null);
		}

		Map<String, Schema> properties = schema.getProperties();
		if (properties != null) {
			properties.forEach((name, propSchema) -> convertLongToString(propSchema));
		}

		if (schema.getItems() != null) {
			convertLongToString(schema.getItems());
		}
	}

	@SuppressWarnings("rawtypes")
	private boolean isLongType(Schema schema) {
		return ("integer".equals(schema.getType())
			|| (schema.getTypes() != null && schema.getTypes().contains("integer")))
			&& "int64".equals(schema.getFormat());
	}

	private void convertOperationLongToString(Operation operation) {
		if (operation.getRequestBody() != null) {
			convertContentLongToString(operation.getRequestBody().getContent());
		}
		if (operation.getResponses() != null) {
			operation.getResponses().values().stream()
				.map(ApiResponse::getContent)
				.forEach(this::convertContentLongToString);
		}
	}

	private void convertContentLongToString(Content content) {
		if (content == null) {
			return;
		}
		content.values().stream()
			.map(MediaType::getSchema)
			.forEach(this::convertLongToString);
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
