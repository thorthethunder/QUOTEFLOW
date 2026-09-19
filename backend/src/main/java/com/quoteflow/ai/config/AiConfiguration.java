package com.quoteflow.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quoteflow.ai.exception.AiConfigurationException;
import com.quoteflow.ai.provider.ollama.OllamaClient;
import com.quoteflow.ai.structured.StructuredOutputValidator;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.time.Clock;
import java.util.Locale;
import java.util.Set;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfiguration {

	private static final Logger log = LoggerFactory.getLogger(AiConfiguration.class);

	private static final Set<String> IMPLEMENTED = Set.of("OLLAMA");

	@Bean
	@ConditionalOnProperty(prefix = "quoteflow.ai", name = "enabled", havingValue = "true")
	@ConditionalOnProperty(prefix = "quoteflow.ai", name = "provider", havingValue = "OLLAMA", matchIfMissing = true)
	@ConditionalOnProperty(prefix = "quoteflow.ai", name = "adapter", havingValue = "legacy-rest")
	OllamaClient ollamaClient(AiProperties properties, ObjectMapper objectMapper) {
		return new OllamaClient(properties, objectMapper);
	}

	@Bean
	StructuredOutputValidator structuredOutputValidator(
			ObjectMapper objectMapper,
			Validator validator,
			AiProperties properties) {
		return new StructuredOutputValidator(objectMapper, validator, properties.getMaxResponseChars());
	}

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	@Order(60)
	ApplicationRunner aiStartupValidator(AiProperties properties, Environment environment) {
		return args -> validate(properties, environment);
	}

	static void validate(AiProperties properties, Environment environment) {
		if (!properties.isEnabled()) {
			log.info("ai.disabled — core SaaS independent of AI providers");
			return;
		}
		String provider = properties.getProvider() == null
				? ""
				: properties.getProvider().trim().toUpperCase(Locale.ROOT);
		if (!IMPLEMENTED.contains(provider)) {
			throw new AiConfigurationException(
					"AI_ENABLED=true but AI_PROVIDER=" + provider
							+ " is not implemented (supported: OLLAMA)");
		}
		if ("OLLAMA".equals(provider)) {
			if (!StringUtils.hasText(properties.getOllama().getBaseUrl())) {
				throw new AiConfigurationException("AI_PROVIDER=OLLAMA requires OLLAMA_BASE_URL");
			}
			if (!StringUtils.hasText(properties.getOllama().getModel())) {
				throw new AiConfigurationException("AI_PROVIDER=OLLAMA requires OLLAMA_MODEL");
			}
			String base = properties.getOllama().getBaseUrl().trim().toLowerCase(Locale.ROOT);
			if (!(base.startsWith("http://") || base.startsWith("https://"))) {
				throw new AiConfigurationException("OLLAMA_BASE_URL must be an http(s) URL");
			}
		}
		boolean prod = Arrays.stream(environment.getActiveProfiles())
				.anyMatch(p -> p.equalsIgnoreCase("prod"));
		log.info(
				"ai.enabled provider={} model={} prodProfile={} logPrompts={}",
				provider,
				properties.getOllama().getModel(),
				prod,
				properties.isLogPrompts());
	}
}
