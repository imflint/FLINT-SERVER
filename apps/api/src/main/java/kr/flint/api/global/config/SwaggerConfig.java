package kr.flint.api.global.config;

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
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Configuration
public class SwaggerConfig {

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
            properties.forEach((name, propSchema) -> {
                convertLongToString(propSchema);
            });
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
	public OpenApiCustomizer authPublicEndpointsCustomizer() {
		return openApi -> {
			if (openApi.getPaths() == null) return;

			Map<String, List<String>> publicByMethod = Map.ofEntries(
				Map.entry("/api/v1/auth/social/verify", List.of("POST")),
				Map.entry("/api/v1/auth/logout", List.of("POST")),
				Map.entry("/api/v1/auth/signup", List.of("POST")),
				Map.entry("/api/v1/auth/refresh", List.of("POST")),
				Map.entry("/api/v1/bookmarks/{collectionId}", List.of("GET")),
				Map.entry("/api/v1/contents/search", List.of("GET")),
				Map.entry("/api/v1/search/contents", List.of("GET")),
				Map.entry("/api/v1/terms", List.of("GET")),
				Map.entry("/api/v1/terms/{termsId}", List.of("GET")),
				Map.entry("/api/v1/users/nickname/check", List.of("GET")),
				Map.entry("/api/v1/users/{userId}", List.of("GET")),
				Map.entry("/api/v1/users/{userId}/keywords", List.of("GET")),
				Map.entry("/api/v1/collections", List.of("GET"))
			);

			publicByMethod.forEach((path, methods) -> {
				PathItem item = openApi.getPaths().get(path);
				if (item == null) return;

				for (String m : methods) {
					switch (m) {
						case "GET" -> { if (item.getGet() != null) item.getGet().setSecurity(List.of()); }
						case "POST" -> { if (item.getPost() != null) item.getPost().setSecurity(List.of()); }
						case "PUT" -> { if (item.getPut() != null) item.getPut().setSecurity(List.of()); }
						case "PATCH" -> { if (item.getPatch() != null) item.getPatch().setSecurity(List.of()); }
						case "DELETE" -> { if (item.getDelete() != null) item.getDelete().setSecurity(List.of()); }
					}
				}
			});
		};
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
				.addOpenApiCustomizer(authPublicEndpointsCustomizer())
				.packagesToScan(basePackage)
                .build();
    }
}
