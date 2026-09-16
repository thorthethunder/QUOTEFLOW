package com.quoteflow.ai.provider;

/**
 * Provider-neutral text generation response. Content is untrusted input.
 */
public record AiResponse(
		String content,
		String provider,
		String model,
		Integer inputTokens,
		Integer outputTokens,
		long latencyMs
) {

	public AiResponse {
		if (content == null) {
			content = "";
		}
	}

	public int totalTokens() {
		int in = inputTokens == null ? 0 : inputTokens;
		int out = outputTokens == null ? 0 : outputTokens;
		return in + out;
	}
}
