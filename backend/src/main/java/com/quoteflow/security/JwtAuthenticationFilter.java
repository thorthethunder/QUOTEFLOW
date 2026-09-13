package com.quoteflow.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtService jwtService;

	public JwtAuthenticationFilter(JwtService jwtService) {
		this.jwtService = jwtService;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header == null || header.isBlank()) {
			filterChain.doFilter(request, response);
			return;
		}
		if (!header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
			filterChain.doFilter(request, response);
			return;
		}

		String token = header.substring(BEARER_PREFIX.length()).trim();
		if (token.isEmpty()) {
			filterChain.doFilter(request, response);
			return;
		}

		try {
			AuthenticatedUser principal = jwtService.parseAndValidate(token);
			var authorities = principal.authorityNames().stream().map(SimpleGrantedAuthority::new).toList();
			var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
			authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
			SecurityContextHolder.getContext().setAuthentication(authentication);
		}
		catch (InvalidAccessTokenException ex) {
			log.debug("Rejected access token: {}", ex.getMessage());
			SecurityContextHolder.clearContext();
		}
		catch (RuntimeException ex) {
			log.debug("Rejected malformed access token");
			SecurityContextHolder.clearContext();
		}

		filterChain.doFilter(request, response);
	}
}
