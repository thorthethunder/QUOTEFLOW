package com.quoteflow.ai.provider;

/**
 * Supported / reserved AI provider types.
 * Only {@link #OLLAMA} is implemented in AI Phase 1.
 */
public enum AiProviderType {
	OLLAMA,
	OPENAI,
	GEMINI,
	ANTHROPIC,
	AZURE_OPENAI,
	SELF_HOSTED,
	DISABLED,
	FAKE
}
