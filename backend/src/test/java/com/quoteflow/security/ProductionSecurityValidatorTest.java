package com.quoteflow.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionSecurityValidatorTest {

	@Test
	void rejectsWeakJwtSecretInProd() {
		SecurityProperties props = new SecurityProperties();
		props.getJwt().setSecret("local-dev-only-quoteflow-jwt-signing-secret-min-32-chars");
		props.getCors().setAllowedOrigins(java.util.List.of("https://app.example.com"));
		MockEnvironment env = new MockEnvironment();
		env.setActiveProfiles("prod");
		ProductionSecurityValidator validator = new ProductionSecurityValidator(env, props);
		assertThatThrownBy(() -> validator.run(null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("JWT_SECRET");
	}

	@Test
	void rejectsWildcardCorsInProd() {
		SecurityProperties props = new SecurityProperties();
		props.getJwt().setSecret("production-grade-jwt-signing-secret-value-32b+");
		props.getCors().setAllowedOrigins(java.util.List.of("*"));
		MockEnvironment env = new MockEnvironment();
		env.setActiveProfiles("prod");
		ProductionSecurityValidator validator = new ProductionSecurityValidator(env, props);
		assertThatThrownBy(() -> validator.run(null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("CORS");
	}

	@Test
	void acceptsStrongSecretAndExplicitOrigin() {
		SecurityProperties props = new SecurityProperties();
		props.getJwt().setSecret("production-grade-jwt-signing-secret-value-32b+");
		props.getCors().setAllowedOrigins(java.util.List.of("https://app.example.com"));
		MockEnvironment env = new MockEnvironment();
		env.setActiveProfiles("prod");
		ProductionSecurityValidator validator = new ProductionSecurityValidator(env, props);
		assertThatCode(() -> validator.run(null)).doesNotThrowAnyException();
	}

	@Test
	void skipsWhenNotProd() {
		SecurityProperties props = new SecurityProperties();
		props.getJwt().setSecret("weak");
		MockEnvironment env = new MockEnvironment();
		env.setActiveProfiles("test");
		ProductionSecurityValidator validator = new ProductionSecurityValidator(env, props);
		assertThatCode(() -> validator.run(null)).doesNotThrowAnyException();
	}
}
