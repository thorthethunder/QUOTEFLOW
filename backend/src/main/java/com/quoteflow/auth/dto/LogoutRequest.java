package com.quoteflow.auth.dto;

import jakarta.validation.constraints.Size;

/**
 * Refresh token may come from HttpOnly cookie (preferred) or JSON body (API/tests).
 */
public record LogoutRequest(
		@Size(min = 20, max = 512) String refreshToken
) {
}
