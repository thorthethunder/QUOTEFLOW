package com.quoteflow.common;

import java.util.Locale;

/**
 * Login emails are trimmed and lowercased before persistence.
 * Global uniqueness is enforced by the database on the normalized value.
 * DB CHECK (V2): email = lower(btrim(email)).
 */
public final class EmailNormalizer {

	private EmailNormalizer() {
	}

	public static String normalize(String email) {
		if (email == null) {
			return null;
		}
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
