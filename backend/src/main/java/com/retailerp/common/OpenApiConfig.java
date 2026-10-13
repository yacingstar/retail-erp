package com.retailerp.common;

import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

/**
 * Adds an "Authorize" button to Swagger UI (http://localhost:8080/swagger-ui.html): paste the token from
 * POST /api/auth/login and every "Try it out" call sends it as a Bearer header.
 */
@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(info = @Info(title = "Retail ERP API", version = "v1"), security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}
