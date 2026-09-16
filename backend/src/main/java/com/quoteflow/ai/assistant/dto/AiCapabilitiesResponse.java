package com.quoteflow.ai.assistant.dto;

public record AiCapabilitiesResponse(
		boolean enabled,
		boolean quoteAssistant,
		String provider,
		String model
) {
}
