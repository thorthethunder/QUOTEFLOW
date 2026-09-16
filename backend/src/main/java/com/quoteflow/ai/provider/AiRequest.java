package com.quoteflow.ai.provider;

import java.util.UUID;

/**
 * Provider-neutral text generation request.
 * Tenant identity is metadata only — never derived or overridden by the model.
 */
public record AiRequest(
		String systemPrompt,
		String userPrompt,
		AiFeature feature,
		AiGenerationOptions options,
		UUID businessId,
		UUID userId
) {

	public AiRequest {
		if (userPrompt == null || userPrompt.isBlank()) {
			throw new IllegalArgumentException("userPrompt is required");
		}
		if (feature == null) {
			throw new IllegalArgumentException("feature is required");
		}
		if (options == null) {
			options = AiGenerationOptions.defaults();
		}
		if (systemPrompt != null && systemPrompt.length() > 32_000) {
			throw new IllegalArgumentException("systemPrompt exceeds max length");
		}
		if (userPrompt.length() > 64_000) {
			throw new IllegalArgumentException("userPrompt exceeds max length");
		}
	}

	public static AiRequest of(AiFeature feature, String userPrompt) {
		return new AiRequest(null, userPrompt, feature, AiGenerationOptions.defaults(), null, null);
	}
}
