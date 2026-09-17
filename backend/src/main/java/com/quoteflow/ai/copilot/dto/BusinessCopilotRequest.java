package com.quoteflow.ai.copilot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BusinessCopilotRequest(
		@NotBlank
		@Size(max = 2000)
		String message
) {
}
