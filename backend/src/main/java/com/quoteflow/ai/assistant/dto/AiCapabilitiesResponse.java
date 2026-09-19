package com.quoteflow.ai.assistant.dto;

import java.time.Instant;
import java.util.Map;

public record AiCapabilitiesResponse(
		boolean enabled,
		boolean quoteAssistant,
		boolean businessCopilot,
		boolean aiActions,
		String provider,
		String model,
		Map<String, FeatureUsageCapability> features
) {
	public AiCapabilitiesResponse(boolean enabled, boolean quoteAssistant, boolean businessCopilot, String provider, String model) {
		this(enabled, quoteAssistant, businessCopilot, false, provider, model, Map.of());
	}

	public AiCapabilitiesResponse(boolean enabled, boolean quoteAssistant, String provider, String model) {
		this(enabled, quoteAssistant, false, false, provider, model, Map.of());
	}

	public AiCapabilitiesResponse(
			boolean enabled,
			boolean quoteAssistant,
			boolean businessCopilot,
			boolean aiActions,
			String provider,
			String model) {
		this(enabled, quoteAssistant, businessCopilot, aiActions, provider, model, Map.of());
	}

	public record FeatureUsageCapability(
			boolean entitled,
			int used,
			int limit,
			int remaining,
			String period,
			Instant resetAt
	) {
	}
}
