package com.quoteflow.ai.health;

import com.quoteflow.ai.config.AiProperties;
import com.quoteflow.ai.provider.AiProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Optional AI availability signal. Always contributes UP so Ollama outage cannot
 * mark the application unhealthy or trip readiness (readiness group remains db-only).
 */
@Component
public class AiHealthIndicator implements HealthIndicator {

	private final AiProperties properties;
	private final AiProvider aiProvider;

	public AiHealthIndicator(AiProperties properties, AiProvider aiProvider) {
		this.properties = properties;
		this.aiProvider = aiProvider;
	}

	@Override
	public Health health() {
		if (!properties.isEnabled()) {
			return Health.up()
					.withDetail("enabled", false)
					.withDetail("provider", "DISABLED")
					.build();
		}
		boolean available = aiProvider.isAvailable();
		return Health.up()
				.withDetail("enabled", true)
				.withDetail("provider", aiProvider.providerName())
				.withDetail("model", aiProvider.model())
				.withDetail("aiAvailable", available)
				.build();
	}
}
