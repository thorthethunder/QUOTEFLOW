package com.quoteflow.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * Production must not start with a missing or known-weak JWT signing secret.
 */
@Component
public class ProductionSecurityValidator implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(ProductionSecurityValidator.class);

	private static final Set<String> FORBIDDEN_SECRETS = Set.of(
			"secret",
			"changeme",
			"quoteflow123",
			"local-dev-only-quoteflow-jwt-signing-secret-min-32-chars",
			"test-only-quoteflow-jwt-signing-secret-min-32-chars!!");

	private final Environment environment;
	private final SecurityProperties securityProperties;

	public ProductionSecurityValidator(Environment environment, SecurityProperties securityProperties) {
		this.environment = environment;
		this.securityProperties = securityProperties;
	}

	@Override
	public void run(ApplicationArguments args) {
		boolean prod = Arrays.stream(environment.getActiveProfiles())
				.anyMatch(p -> p.equalsIgnoreCase("prod"));
		if (!prod) {
			return;
		}

		String secret = securityProperties.getJwt().getSecret();
		if (!StringUtils.hasText(secret) || secret.getBytes().length < 32) {
			throw new IllegalStateException("Production requires JWT_SECRET of at least 32 bytes");
		}
		String normalized = secret.trim().toLowerCase(Locale.ROOT);
		if (FORBIDDEN_SECRETS.contains(normalized) || normalized.contains("local-dev-only") || normalized.contains("test-only")) {
			throw new IllegalStateException("Production JWT_SECRET must not use a known development/default value");
		}
		if (securityProperties.getCors().getAllowedOrigins() == null
				|| securityProperties.getCors().getAllowedOrigins().isEmpty()
				|| securityProperties.getCors().getAllowedOrigins().stream().anyMatch("*"::equals)) {
			throw new IllegalStateException("Production requires explicit CORS_ALLOWED_ORIGINS (no wildcard)");
		}
		log.info("Production JWT and CORS configuration validated");
	}
}
