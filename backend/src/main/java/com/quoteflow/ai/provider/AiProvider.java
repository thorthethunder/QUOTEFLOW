package com.quoteflow.ai.provider;

import com.quoteflow.ai.structured.StructuredAiRequest;
import com.quoteflow.ai.structured.StructuredAiResponse;

/**
 * Provider-neutral AI contract. Implementations must not access business repositories
 * or decide authorization / tenant ownership.
 */
public interface AiProvider {

	AiProviderType type();

	String providerName();

	/** Configured model id, or empty when disabled. */
	String model();

	boolean isEnabled();

	/** Lightweight availability (no expensive generation). */
	boolean isAvailable();

	AiResponse generate(AiRequest request);

	<T> StructuredAiResponse<T> generateStructured(StructuredAiRequest<T> request);
}
