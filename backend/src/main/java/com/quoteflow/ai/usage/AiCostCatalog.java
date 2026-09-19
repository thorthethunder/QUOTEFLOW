package com.quoteflow.ai.usage;

import com.quoteflow.ai.provider.AiProviderType;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class AiCostCatalog {

	public AiCostEstimate estimateProviderApiCost(
			AiProviderType providerType,
			String provider,
			String model,
			AiUsageType usageType,
			Integer inputTokens,
			Integer outputTokens,
			Integer embeddingCount,
			Instant occurredAt) {
		if (providerType == AiProviderType.OLLAMA || "OLLAMA".equalsIgnoreCase(provider)) {
			return AiCostEstimate.zero("USD");
		}
		if ("HASH".equalsIgnoreCase(provider)) {
			return AiCostEstimate.zero("USD");
		}
		if (providerType == AiProviderType.DISABLED || "DISABLED".equalsIgnoreCase(provider)) {
			return AiCostEstimate.unknown();
		}
		return AiCostEstimate.unknown();
	}
}
