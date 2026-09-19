package com.quoteflow.ai.usage;

import com.quoteflow.ai.provider.AiFeature;
import com.quoteflow.ai.provider.AiProviderType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Metadata-only AI usage event. Never store prompts, responses, retrieved chunks, email bodies, or customer content.
 */
public record AiUsageEvent(
		AiProviderType providerType,
		String providerName,
		String model,
		AiFeature feature,
		String operation,
		AiUsageType usageType,
		boolean success,
		String errorCode,
		long latencyMs,
		Integer inputTokens,
		Integer outputTokens,
		Integer totalTokens,
		Integer inputCharacters,
		Integer embeddingCount,
		Integer retrievedChunkCount,
		BigDecimal estimatedProviderCost,
		String currency,
		UUID businessId,
		UUID userId,
		Integer toolCallCount,
		String billingPeriodKey,
		String referenceKey
) {
	public AiUsageEvent(
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
			UUID userId) {
		this(providerType, providerName, model, feature, success, errorCode, latencyMs,
				inputTokens, outputTokens, businessId, userId, null);
	}

	public AiUsageEvent(
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
			UUID userId,
			Integer toolCallCount) {
		this(providerType, providerName, model, feature,
				feature == null ? "unknown" : feature.name().toLowerCase(),
				AiUsageType.CHAT,
				success,
				errorCode,
				latencyMs,
				inputTokens,
				outputTokens,
				null,
				null,
				null,
				null,
				null,
				null,
				businessId,
				userId,
				toolCallCount,
				null,
				null);
	}

	public static AiUsageEvent customerAllowance(
			AiFeature feature,
			String operation,
			UUID businessId,
			UUID userId,
			boolean success,
			String errorCode,
			String billingPeriodKey) {
		return new AiUsageEvent(
				AiProviderType.DISABLED,
				"INTERNAL",
				"",
				feature,
				operation == null || operation.isBlank() ? feature.name().toLowerCase() : operation,
				AiUsageType.CUSTOMER_ALLOWANCE,
				success,
				errorCode,
				0L,
				null,
				null,
				null,
				null,
				null,
				null,
				BigDecimal.ZERO.setScale(8),
				"USD",
				businessId,
				userId,
				null,
				billingPeriodKey,
				null);
	}

	public static AiUsageEvent embedding(
			AiProviderType providerType,
			String providerName,
			String model,
			AiFeature feature,
			String operation,
			boolean success,
			String errorCode,
			long latencyMs,
			Integer inputCharacters,
			Integer embeddingCount,
			UUID businessId,
			UUID userId,
			String referenceKey) {
		return new AiUsageEvent(
				providerType,
				providerName,
				model,
				feature,
				operation,
				AiUsageType.EMBEDDING,
				success,
				errorCode,
				latencyMs,
				null,
				null,
				null,
				inputCharacters,
				embeddingCount,
				null,
				null,
				null,
				businessId,
				userId,
				null,
				null,
				referenceKey);
	}
}
