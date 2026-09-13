package com.quoteflow.auth;

import com.quoteflow.auth.dto.MeResponse;
import com.quoteflow.security.AuthenticatedUser;
import com.quoteflow.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Current user")
@SecurityRequirement(name = "bearerAuth")
public class MeController {

	private final AuthService authService;

	public MeController(AuthService authService) {
		this.authService = authService;
	}

	@GetMapping
	@Operation(summary = "Return the authenticated user profile")
	public MeResponse me() {
		AuthenticatedUser principal = SecurityUtils.requireCurrentUser();
		return authService.me(principal);
	}
}
