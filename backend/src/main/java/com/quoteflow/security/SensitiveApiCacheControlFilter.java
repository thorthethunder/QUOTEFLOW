package com.quoteflow.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Prevent intermediate/public caching of authenticated API responses.
 * Static Angular assets are served separately and may remain cacheable.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class SensitiveApiCacheControlFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		String path = request.getRequestURI();
		boolean sensitiveApi = path != null && path.startsWith("/api/v1/");
		if (sensitiveApi) {
			response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
			response.setHeader(HttpHeaders.PRAGMA, "no-cache");
		}
		filterChain.doFilter(request, response);
		if (sensitiveApi && !response.isCommitted()) {
			response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
			response.setHeader(HttpHeaders.PRAGMA, "no-cache");
		}
	}
}
