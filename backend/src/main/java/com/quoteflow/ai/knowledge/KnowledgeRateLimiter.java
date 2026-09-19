package com.quoteflow.ai.knowledge;

import com.quoteflow.ai.config.AiProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class KnowledgeRateLimiter {

	private final AiProperties properties;
	private final Clock clock;
	private final Map<String, Window> windows = new ConcurrentHashMap<>();

	public KnowledgeRateLimiter(AiProperties properties, Clock clock) {
		this.properties = properties;
		this.clock = clock;
	}

	public boolean tryAcquire(UUID businessId, UUID userId) {
		long minute = Instant.now(clock).getEpochSecond() / 60L;
		return acquire("u:" + businessId + ":" + userId, minute, properties.getKnowledge().getQueryPerUserPerMinute())
				&& acquire("t:" + businessId, minute, properties.getKnowledge().getQueryPerTenantPerMinute());
	}

	private boolean acquire(String key, long minute, int limit) {
		Window next = windows.compute(key, (ignored, existing) -> {
			if (existing == null || existing.minute != minute) {
				return new Window(minute, 1);
			}
			return new Window(minute, existing.count + 1);
		});
		return next.count <= limit;
	}

	private record Window(long minute, int count) {
	}
}
