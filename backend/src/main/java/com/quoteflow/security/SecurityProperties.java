package com.quoteflow.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "quoteflow.security")
public class SecurityProperties {

	private final Jwt jwt = new Jwt();
	private final Cors cors = new Cors();
	private final RefreshCookie refreshCookie = new RefreshCookie();
	private int bcryptStrength = 12;
	private final AuthRateLimit authRateLimit = new AuthRateLimit();

	public Jwt getJwt() {
		return jwt;
	}

	public Cors getCors() {
		return cors;
	}

	public RefreshCookie getRefreshCookie() {
		return refreshCookie;
	}

	public int getBcryptStrength() {
		return bcryptStrength;
	}

	public void setBcryptStrength(int bcryptStrength) {
		this.bcryptStrength = bcryptStrength;
	}

	public AuthRateLimit getAuthRateLimit() {
		return authRateLimit;
	}

	public static class Jwt {
		private String secret;
		private String issuer = "quoteflow";
		private String audience = "quoteflow-api";
		private Duration accessTokenTtl = Duration.ofMinutes(15);
		private Duration refreshTokenTtl = Duration.ofDays(14);

		public String getSecret() {
			return secret;
		}

		public void setSecret(String secret) {
			this.secret = secret;
		}

		public String getIssuer() {
			return issuer;
		}

		public void setIssuer(String issuer) {
			this.issuer = issuer;
		}

		public String getAudience() {
			return audience;
		}

		public void setAudience(String audience) {
			this.audience = audience;
		}

		public Duration getAccessTokenTtl() {
			return accessTokenTtl;
		}

		public void setAccessTokenTtl(Duration accessTokenTtl) {
			this.accessTokenTtl = accessTokenTtl;
		}

		public Duration getRefreshTokenTtl() {
			return refreshTokenTtl;
		}

		public void setRefreshTokenTtl(Duration refreshTokenTtl) {
			this.refreshTokenTtl = refreshTokenTtl;
		}
	}

	public static class Cors {
		private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:4200"));

		public List<String> getAllowedOrigins() {
			return allowedOrigins;
		}

		public void setAllowedOrigins(List<String> allowedOrigins) {
			this.allowedOrigins = allowedOrigins;
		}
	}

	/**
	 * Browser refresh-token cookie (Phase 4).
	 * Production: Secure=true, SameSite=Lax (same-site deployment) or None with CSRF when cross-site.
	 */
	public static class RefreshCookie {
		private String name = "qf_refresh";
		private String path = "/api/v1/auth";
		/** false for local HTTP; production profile forces true via config. */
		private boolean secure = false;
		private String sameSite = "Lax";
		/** When false, refresh token is omitted from JSON (cookie-only for browsers). */
		private boolean returnInBody = false;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getPath() {
			return path;
		}

		public void setPath(String path) {
			this.path = path;
		}

		public boolean isSecure() {
			return secure;
		}

		public void setSecure(boolean secure) {
			this.secure = secure;
		}

		public String getSameSite() {
			return sameSite;
		}

		public void setSameSite(String sameSite) {
			this.sameSite = sameSite;
		}

		public boolean isReturnInBody() {
			return returnInBody;
		}

		public void setReturnInBody(boolean returnInBody) {
			this.returnInBody = returnInBody;
		}
	}

	public static class AuthRateLimit {
		private boolean enabled = true;
		private int registerPerMinute = 10;
		private int loginPerMinute = 20;
		private int refreshPerMinute = 60;

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public int getRegisterPerMinute() {
			return registerPerMinute;
		}

		public void setRegisterPerMinute(int registerPerMinute) {
			this.registerPerMinute = registerPerMinute;
		}

		public int getLoginPerMinute() {
			return loginPerMinute;
		}

		public void setLoginPerMinute(int loginPerMinute) {
			this.loginPerMinute = loginPerMinute;
		}

		public int getRefreshPerMinute() {
			return refreshPerMinute;
		}

		public void setRefreshPerMinute(int refreshPerMinute) {
			this.refreshPerMinute = refreshPerMinute;
		}
	}
}
