package com.quoteflow.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	@Bean
	OpenAPI quoteflowOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("QuoteFlow API")
						.version("v1")
						.description("QuoteFlow backend API. Auth, customers, quotations, quotation PDF."))
				.components(new Components().addSecuritySchemes(
						"bearerAuth",
						new SecurityScheme()
								.type(SecurityScheme.Type.HTTP)
								.scheme("bearer")
								.bearerFormat("JWT")));
	}
}
