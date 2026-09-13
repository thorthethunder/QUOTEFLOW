package com.quoteflow.auth;

import com.quoteflow.auth.dto.AuthResponse;
import com.quoteflow.security.SecurityProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Component
public class RefreshTokenCookieService {

	private final SecurityProperties securityProperties;

	public RefreshTokenCookieService(SecurityProperties securityProperties) {
		this.securityProperties = securityProperties;
	}

	public void writeRefreshCookie(HttpServletResponse response, String rawRefreshToken) {
		SecurityProperties.RefreshCookie cfg = securityProperties.getRefreshCookie();
		Duration maxAge = securityProperties.getJwt().getRefreshTokenTtl();
		ResponseCookie cookie = ResponseCookie.from(cfg.getName(), rawRefreshToken)
				.httpOnly(true)
				.secure(cfg.isSecure())
				.path(cfg.getPath())
				.maxAge(maxAge)
				.sameSite(cfg.getSameSite())
				.build();
		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
	}

	public void clearRefreshCookie(HttpServletResponse response) {
		SecurityProperties.RefreshCookie cfg = securityProperties.getRefreshCookie();
		ResponseCookie cookie = ResponseCookie.from(cfg.getName(), "")
				.httpOnly(true)
				.secure(cfg.isSecure())
				.path(cfg.getPath())
				.maxAge(0)
				.sameSite(cfg.getSameSite())
				.build();
		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
	}

	public String readRefreshToken(HttpServletRequest request, String bodyToken) {
		if (StringUtils.hasText(bodyToken)) {
			return bodyToken.trim();
		}
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		String name = securityProperties.getRefreshCookie().getName();
		for (Cookie cookie : cookies) {
			if (name.equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
				return cookie.getValue();
			}
		}
		return null;
	}

	public AuthResponse toClientResponse(AuthResponse full) {
		if (securityProperties.getRefreshCookie().isReturnInBody()) {
			return full;
		}
		return new AuthResponse(
				full.accessToken(),
				full.tokenType(),
				full.expiresIn(),
				full.accessTokenExpiresAt(),
				null,
				full.user());
	}
}
