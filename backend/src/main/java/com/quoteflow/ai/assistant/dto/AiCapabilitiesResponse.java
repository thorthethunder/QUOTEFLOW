package com.quoteflow.ai.assistant.dto;

public record AiCapabilitiesResponse(
		boolean enabled,
		boolean quoteAssistant,
		boolean businessCopilot,
		boolean aiActions,
		String provider,
		String model
) {
	public AiCapabilitiesResponse(boolean enabled, boolean quoteAssistant, boolean businessCopilot, String provider, String model) {
		this(enabled, quoteAssistant, businessCopilot, false, provider, model);
	}

	public AiCapabilitiesResponse(boolean enabled, boolean quoteAssistant, String provider, String model) {
		this(enabled, quoteAssistant, false, false, provider, model);
	}
}
