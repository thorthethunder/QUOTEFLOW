package com.quoteflow.auth;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Opaque refresh tokens are high-entropy random values.
 * SHA-256 is used for persistence lookup (not BCrypt) because tokens are not human passwords.
 */
@Component
public class RefreshTokenHasher {

	private static final int TOKEN_BYTES = 32;
	private final SecureRandom secureRandom = new SecureRandom();

	public String generateOpaqueToken() {
		byte[] bytes = new byte[TOKEN_BYTES];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	public String hash(String rawToken) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hashed);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 not available", ex);
		}
	}
}
