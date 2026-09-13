package com.quoteflow.auth;

import com.quoteflow.auth.dto.AuthResponse;
import com.quoteflow.auth.dto.LoginRequest;
import com.quoteflow.auth.dto.LogoutRequest;
import com.quoteflow.auth.dto.RefreshRequest;
import com.quoteflow.auth.dto.RegisterRequest;
import com.quoteflow.security.AuthRateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Registration, login, refresh, and logout")
public class AuthController {

	private final AuthService authService;
	private final AuthRateLimiter authRateLimiter;
	private final RefreshTokenCookieService refreshTokenCookieService;

	public AuthController(
			AuthService authService,
			AuthRateLimiter authRateLimiter,
			RefreshTokenCookieService refreshTokenCookieService) {
		this.authService = authService;
		this.authRateLimiter = authRateLimiter;
		this.refreshTokenCookieService = refreshTokenCookieService;
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Register a new business and OWNER user")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Registered",
					content = @Content(schema = @Schema(implementation = AuthResponse.class))),
			@ApiResponse(responseCode = "400", description = "Validation error", content = @Content),
			@ApiResponse(responseCode = "409", description = "Registration conflict", content = @Content),
			@ApiResponse(responseCode = "429", description = "Rate limited", content = @Content)
	})
	public AuthResponse register(
			@Valid @RequestBody RegisterRequest request,
			HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		authRateLimiter.checkRegister(clientKey(httpRequest));
		AuthResponse issued = authService.register(request);
		refreshTokenCookieService.writeRefreshCookie(httpResponse, issued.refreshToken());
		return refreshTokenCookieService.toClientResponse(issued);
	}

	@PostMapping("/login")
	@Operation(summary = "Login with email and password")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Authenticated",
					content = @Content(schema = @Schema(implementation = AuthResponse.class))),
			@ApiResponse(responseCode = "400", description = "Validation error", content = @Content),
			@ApiResponse(responseCode = "401", description = "Invalid credentials", content = @Content),
			@ApiResponse(responseCode = "403", description = "Account or business not eligible", content = @Content),
			@ApiResponse(responseCode = "429", description = "Rate limited", content = @Content)
	})
	public AuthResponse login(
			@Valid @RequestBody LoginRequest request,
			HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		authRateLimiter.checkLogin(clientKey(httpRequest));
		AuthResponse issued = authService.login(request);
		refreshTokenCookieService.writeRefreshCookie(httpResponse, issued.refreshToken());
		return refreshTokenCookieService.toClientResponse(issued);
	}

	@PostMapping("/refresh")
	@Operation(summary = "Rotate refresh token and issue a new access token")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Refreshed",
					content = @Content(schema = @Schema(implementation = AuthResponse.class))),
			@ApiResponse(responseCode = "401", description = "Invalid refresh token", content = @Content),
			@ApiResponse(responseCode = "403", description = "Account or business not eligible", content = @Content),
			@ApiResponse(responseCode = "429", description = "Rate limited", content = @Content)
	})
	public AuthResponse refresh(
			@RequestBody(required = false) @Valid RefreshRequest request,
			HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		authRateLimiter.checkRefresh(clientKey(httpRequest));
		String raw = refreshTokenCookieService.readRefreshToken(
				httpRequest, request == null ? null : request.refreshToken());
		if (!StringUtils.hasText(raw)) {
			throw new AuthenticationFailedException("Invalid refresh token.");
		}
		AuthResponse issued = authService.refresh(raw);
		refreshTokenCookieService.writeRefreshCookie(httpResponse, issued.refreshToken());
		return refreshTokenCookieService.toClientResponse(issued);
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Revoke the presented refresh token")
	@ApiResponses({
			@ApiResponse(responseCode = "204", description = "Logged out"),
			@ApiResponse(responseCode = "400", description = "Validation error", content = @Content)
	})
	public void logout(
			@RequestBody(required = false) @Valid LogoutRequest request,
			HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		String raw = refreshTokenCookieService.readRefreshToken(
				httpRequest, request == null ? null : request.refreshToken());
		if (StringUtils.hasText(raw)) {
			authService.logout(raw);
		}
		refreshTokenCookieService.clearRefreshCookie(httpResponse);
	}

	private static String clientKey(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) {
			return forwarded.split(",")[0].trim();
		}
		return request.getRemoteAddr();
	}
}
