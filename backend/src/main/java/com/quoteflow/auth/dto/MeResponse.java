package com.quoteflow.auth.dto;

import com.quoteflow.identity.TenantRole;

import java.util.UUID;

public record MeResponse(
		UUID userId,
		UUID businessId,
		String businessName,
		String firstName,
		String lastName,
		String email,
		TenantRole tenantRole
) {
}
