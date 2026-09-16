package com.quoteflow.ai.usage;

import com.quoteflow.ai.provider.AiFeature;
import com.quoteflow.ai.provider.AiProviderType;

import java.util.UUID;

/**
 * Lightweight AI usage event. Distinguishes provider API monetary cost (often zero for local Ollama)
 * from compute/infrastructure cost (not zero). No billing enforcement in Phase 1.
 */
public record AiUsageEvent(
		AiProviderType providerType,
		String providerName,
		String model,
		AiFeature feature,
		boolean success,
		String errorCode,
		long latencyMs,
		Integer inputTokens,
		Integer outputTokens,
		UUID businessId,
		UUID userId
) {
}
