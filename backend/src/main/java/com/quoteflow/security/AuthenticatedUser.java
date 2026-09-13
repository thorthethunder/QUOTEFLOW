package com.quoteflow.security;

import com.quoteflow.identity.TenantRole;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Authenticated principal derived from a verified access JWT.
 * Trusted tenant identity lives here — never from client-supplied businessId.
 */
public final class AuthenticatedUser {

	private final UUID userId;
	private final UUID businessId;
	private final TenantRole tenantRole;
	private final String email;

	public AuthenticatedUser(UUID userId, UUID businessId, TenantRole tenantRole, String email) {
		this.userId = Objects.requireNonNull(userId, "userId");
		this.businessId = Objects.requireNonNull(businessId, "businessId");
		this.tenantRole = Objects.requireNonNull(tenantRole, "tenantRole");
		this.email = email;
	}

	public UUID getUserId() {
		return userId;
	}

	public UUID getBusinessId() {
		return businessId;
	}

	public TenantRole getTenantRole() {
		return tenantRole;
	}

	public String getEmail() {
		return email;
	}

	public Collection<String> authorityNames() {
		return List.of("ROLE_" + tenantRole.name());
	}

	@Override
	public String toString() {
		return "AuthenticatedUser{userId=" + userId + ", businessId=" + businessId + ", tenantRole=" + tenantRole + '}';
	}
}
