package com.quoteflow.security;

import com.quoteflow.identity.TenantRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

	public static final String CLAIM_BUSINESS_ID = "businessId";
	public static final String CLAIM_TENANT_ROLE = "tenantRole";

	private final SecurityProperties securityProperties;
	private final SecretKey secretKey;

	public JwtService(SecurityProperties securityProperties) {
		this.securityProperties = securityProperties;
		byte[] keyBytes = securityProperties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
		if (keyBytes.length < 32) {
			throw new IllegalStateException("JWT secret must be at least 32 bytes");
		}
		this.secretKey = Keys.hmacShaKeyFor(keyBytes);
	}

	public IssuedAccessToken issueAccessToken(AuthenticatedUser user) {
		Instant now = Instant.now();
		Instant expiresAt = now.plus(securityProperties.getJwt().getAccessTokenTtl());
		String jti = UUID.randomUUID().toString();
		String token = Jwts.builder()
				.id(jti)
				.issuer(securityProperties.getJwt().getIssuer())
				.audience().add(securityProperties.getJwt().getAudience()).and()
				.subject(user.getUserId().toString())
				.claim(CLAIM_BUSINESS_ID, user.getBusinessId().toString())
				.claim(CLAIM_TENANT_ROLE, user.getTenantRole().name())
				.issuedAt(Date.from(now))
				.expiration(Date.from(expiresAt))
				.signWith(secretKey, Jwts.SIG.HS256)
				.compact();
		return new IssuedAccessToken(token, expiresAt, securityProperties.getJwt().getAccessTokenTtl().toSeconds());
	}

	public AuthenticatedUser parseAndValidate(String token) {
		try {
			Claims claims = Jwts.parser()
					.verifyWith(secretKey)
					.requireIssuer(securityProperties.getJwt().getIssuer())
					.requireAudience(securityProperties.getJwt().getAudience())
					.build()
					.parseSignedClaims(token)
					.getPayload();

			String subject = claims.getSubject();
			String businessId = claims.get(CLAIM_BUSINESS_ID, String.class);
			String tenantRole = claims.get(CLAIM_TENANT_ROLE, String.class);
			if (subject == null || businessId == null || tenantRole == null) {
				throw new InvalidAccessTokenException("Access token is missing required claims");
			}
			return new AuthenticatedUser(
					UUID.fromString(subject),
					UUID.fromString(businessId),
					TenantRole.valueOf(tenantRole),
					null);
		}
		catch (ExpiredJwtException ex) {
			throw new InvalidAccessTokenException("Access token has expired");
		}
		catch (IllegalArgumentException | JwtException ex) {
			throw new InvalidAccessTokenException("Access token is invalid");
		}
	}

	public record IssuedAccessToken(String token, Instant expiresAt, long expiresInSeconds) {
	}
}
