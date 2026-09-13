package com.quoteflow.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.quoteflow.identity.TenantRole;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(
		String accessToken,
		String tokenType,
		long expiresIn,
		Instant accessTokenExpiresAt,
		String refreshToken,
		UserSummary user
) {
	public record UserSummary(
			UUID userId,
			UUID businessId,
			String businessName,
			String email,
			String firstName,
			String lastName,
			TenantRole tenantRole
	) {
	}
}
