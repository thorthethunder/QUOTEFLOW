package com.quoteflow.ai.structured;

import com.fasterxml.jackson.databind.JsonNode;
import com.quoteflow.ai.provider.AiFeature;
import com.quoteflow.ai.provider.AiGenerationOptions;

import java.util.UUID;

/**
 * Provider-neutral structured generation request.
 * {@code jsonSchema} should describe the expected object; response type is validated after parse.
 */
public record StructuredAiRequest<T>(
		String systemPrompt,
		String userPrompt,
		Class<T> responseType,
		JsonNode jsonSchema,
		AiFeature feature,
		AiGenerationOptions options,
		UUID businessId,
		UUID userId
) {

	public StructuredAiRequest {
		if (userPrompt == null || userPrompt.isBlank()) {
			throw new IllegalArgumentException("userPrompt is required");
		}
		if (responseType == null) {
			throw new IllegalArgumentException("responseType is required");
		}
		if (jsonSchema == null || jsonSchema.isNull()) {
			throw new IllegalArgumentException("jsonSchema is required");
		}
		if (feature == null) {
			throw new IllegalArgumentException("feature is required");
		}
		if (options == null) {
			options = AiGenerationOptions.structuredExtraction();
		}
		if (userPrompt.length() > 64_000) {
			throw new IllegalArgumentException("userPrompt exceeds max length");
		}
	}
}
