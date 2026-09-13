package com.quoteflow.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailNormalizerTest {

	@Test
	void trimsAndLowercases() {
		assertThat(EmailNormalizer.normalize("  Ada.Lovelace@Example.COM ")).isEqualTo("ada.lovelace@example.com");
	}

	@Test
	void nullSafe() {
		assertThat(EmailNormalizer.normalize(null)).isNull();
	}
}
