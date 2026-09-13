package com.quoteflow.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Forces deferred CSRF token resolution so the XSRF-TOKEN cookie is written for SPAs.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class CsrfCookieFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(
			HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		CsrfToken csrf = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
		if (csrf != null) {
			csrf.getToken();
		}
		filterChain.doFilter(request, response);
	}
}
