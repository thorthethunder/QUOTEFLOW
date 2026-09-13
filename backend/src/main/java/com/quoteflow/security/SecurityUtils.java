package com.quoteflow.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public final class SecurityUtils {

	private SecurityUtils() {
	}

	public static Optional<AuthenticatedUser> currentUser() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
			return Optional.empty();
		}
		return Optional.of(user);
	}

	public static AuthenticatedUser requireCurrentUser() {
		return currentUser().orElseThrow(() -> new IllegalStateException("Authenticated user required"));
	}
}
