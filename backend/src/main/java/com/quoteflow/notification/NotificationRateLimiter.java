package com.quoteflow.notification;

import com.quoteflow.notification.email.EmailProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory send-rate limiter (tenant + document). Same approach as auth rate limits.
 */
@Component
public class NotificationRateLimiter {

	private final EmailProperties properties;
	private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

	public NotificationRateLimiter(EmailProperties properties) {
		this.properties = properties;
	}

	public boolean tryAcquireTenant(UUID businessId) {
		return tryAcquire("t:" + businessId, properties.getPerTenantPerMinute());
	}

	public boolean tryAcquireDocument(UUID businessId, String referenceType, UUID referenceId) {
		return tryAcquire("d:" + businessId + ":" + referenceType + ":" + referenceId, properties.getPerDocumentPerMinute());
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
