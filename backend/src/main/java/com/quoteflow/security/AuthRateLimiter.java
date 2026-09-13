package com.quoteflow.security;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single-instance in-memory rate limiter for auth endpoints.
 * Not distributed — edge/WAF or Redis may replace this later.
 */
@Component
public class AuthRateLimiter {

	private final SecurityProperties securityProperties;
	private final Map<String, Deque<Long>> windows = new ConcurrentHashMap<>();

	public AuthRateLimiter(SecurityProperties securityProperties) {
		this.securityProperties = securityProperties;
	}

	public void checkRegister(String clientKey) {
		check("register", clientKey, securityProperties.getAuthRateLimit().getRegisterPerMinute());
	}

	public void checkLogin(String clientKey) {
		check("login", clientKey, securityProperties.getAuthRateLimit().getLoginPerMinute());
	}

	public void checkRefresh(String clientKey) {
		check("refresh", clientKey, securityProperties.getAuthRateLimit().getRefreshPerMinute());
	}

	private void check(String action, String clientKey, int limitPerMinute) {
		if (!securityProperties.getAuthRateLimit().isEnabled()) {
			return;
		}
		String key = action + ":" + (clientKey == null || clientKey.isBlank() ? "unknown" : clientKey);
		long now = Instant.now().toEpochMilli();
		long windowStart = now - 60_000L;
		Deque<Long> timestamps = windows.computeIfAbsent(key, k -> new ArrayDeque<>());
		synchronized (timestamps) {
			while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
				timestamps.removeFirst();
			}
			if (timestamps.size() >= limitPerMinute) {
				throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many authentication attempts. Try again later.");
			}
			timestamps.addLast(now);
		}
	}
}
