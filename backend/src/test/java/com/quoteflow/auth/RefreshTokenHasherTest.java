package com.quoteflow.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenHasherTest {

	private final RefreshTokenHasher hasher = new RefreshTokenHasher();

	@Test
	void generatesOpaqueTokensAndDeterministicHashes() {
		String token = hasher.generateOpaqueToken();
		assertThat(token).hasSizeGreaterThan(20);
		assertThat(hasher.hash(token)).isEqualTo(hasher.hash(token));
		assertThat(hasher.hash(token)).hasSize(64);
		assertThat(hasher.hash(token)).isNotEqualTo(token);
	}
}
