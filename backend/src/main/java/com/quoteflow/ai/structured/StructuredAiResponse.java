package com.quoteflow.ai.structured;

/**
 * Validated structured AI result. Value remains untrusted business input —
 * never persist automatically; never treat as authoritative financial totals.
 */
public record StructuredAiResponse<T>(
		T value,
		String rawJson,
		String provider,
		String model,
		Integer inputTokens,
		Integer outputTokens,
		long latencyMs
) {
}
