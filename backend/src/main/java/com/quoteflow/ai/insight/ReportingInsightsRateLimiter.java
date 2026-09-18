package com.quoteflow.ai.insight;

import com.quoteflow.ai.config.AiProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ReportingInsightsRateLimiter {

	private final AiProperties properties;
	private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

	public ReportingInsightsRateLimiter(AiProperties properties) {
		this.properties = properties;
	}

	public boolean tryAcquire(UUID businessId, UUID userId) {
		boolean userOk = tryAcquire(
				"u:" + businessId + ":" + userId,
				properties.getReportingInsights().getPerUserPerMinute());
		boolean tenantOk = tryAcquire(
				"t:" + businessId,
				properties.getReportingInsights().getPerTenantPerMinute());
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
