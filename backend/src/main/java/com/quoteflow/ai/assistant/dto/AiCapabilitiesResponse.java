package com.quoteflow.ai.assistant.dto;

public record AiCapabilitiesResponse(
		boolean enabled,
		boolean quoteAssistant,
		boolean businessCopilot,
		String provider,
		String model
) {
	/** Backward-compatible helper when model exposure is not needed. */
	public AiCapabilitiesResponse(boolean enabled, boolean quoteAssistant, String provider, String model) {
		this(enabled, quoteAssistant, false, provider, model);
	}
}
