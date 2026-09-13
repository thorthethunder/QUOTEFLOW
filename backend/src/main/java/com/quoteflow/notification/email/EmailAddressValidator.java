package com.quoteflow.notification.email;

import java.util.regex.Pattern;

/**
 * Practical recipient validation — not RFC-perfect. Rejects blank, CRLF, and obvious invalids.
 */
public final class EmailAddressValidator {

	private static final Pattern SIMPLE = Pattern.compile(
			"^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

	private EmailAddressValidator() {
	}

	public static boolean isValid(String email) {
		if (email == null) {
			return false;
		}
		String trimmed = email.trim();
		if (trimmed.isEmpty() || trimmed.length() > 320) {
			return false;
		}
		if (trimmed.indexOf('\r') >= 0 || trimmed.indexOf('\n') >= 0) {
			return false;
		}
		return SIMPLE.matcher(trimmed).matches();
	}

	public static String requireValid(String email) {
		if (!isValid(email)) {
			return null;
		}
		return email.trim().toLowerCase();
	}
}
