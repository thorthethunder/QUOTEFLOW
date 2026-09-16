package com.quoteflow.ai.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record QuoteAssistantRequest(
		@NotBlank @Size(max = 4000) String prompt
) {
}
