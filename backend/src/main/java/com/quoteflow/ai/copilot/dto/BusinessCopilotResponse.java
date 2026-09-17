package com.quoteflow.ai.copilot.dto;

import java.util.List;

public record BusinessCopilotResponse(
		String answer,
		List<BusinessCopilotReference> references,
		List<String> warnings
) {
	public BusinessCopilotResponse {
		references = references == null ? List.of() : List.copyOf(references);
		warnings = warnings == null ? List.of() : List.copyOf(warnings);
	}
}
