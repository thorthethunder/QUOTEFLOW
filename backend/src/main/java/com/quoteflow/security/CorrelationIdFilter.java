package com.quoteflow.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Lightweight request correlation for logs and error responses.
 * Not a security authority — never used for authz.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class CorrelationIdFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-Correlation-Id";
	public static final String MDC_KEY = "correlationId";

	private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9_\\-.]{8,64}$");

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		String incoming = request.getHeader(HEADER);
		String correlationId = sanitizeOrGenerate(incoming);
		request.setAttribute(HEADER, correlationId);
		MDC.put(MDC_KEY, correlationId);
		response.setHeader(HEADER, correlationId);
		try {
			filterChain.doFilter(request, response);
		} finally {
			MDC.remove(MDC_KEY);
		}
	}

	public static String sanitizeOrGenerate(String incoming) {
		if (incoming != null) {
			String trimmed = incoming.trim();
			if (SAFE.matcher(trimmed).matches()) {
				return trimmed;
			}
		}
		return UUID.randomUUID().toString();
	}
}
