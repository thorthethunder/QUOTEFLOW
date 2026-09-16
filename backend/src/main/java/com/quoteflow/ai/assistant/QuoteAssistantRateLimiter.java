package com.quoteflow.ai.assistant;

import com.quoteflow.ai.config.AiProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-instance in-memory rate limit for Quote Assistant (user + tenant).
 */
@Component
public class QuoteAssistantRateLimiter {

	private final AiProperties properties;
	private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

	public QuoteAssistantRateLimiter(AiProperties properties) {
		this.properties = properties;
	}

	public boolean tryAcquire(UUID businessId, UUID userId) {
		boolean userOk = tryAcquire(
				"u:" + businessId + ":" + userId,
				properties.getQuoteAssistant().getPerUserPerMinute());
		boolean tenantOk = tryAcquire(
				"t:" + businessId,
				properties.getQuoteAssistant().getPerTenantPerMinute());
		return userOk && tenantOk;
	}

	private boolean tryAcquire(String key, int limit) {
		long minute = Instant.now().getEpochSecond() / 60;
		Window window = windows.compute(key, (k, existing) -> {
			if (existing == null || existing.minute != minute) {
				return new Window(minute, new AtomicInteger(0));
			}
			return existing;
		});
		return window.count.incrementAndGet() <= limit;
	}

	private record Window(long minute, AtomicInteger count) {
	}
}
