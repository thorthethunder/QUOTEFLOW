package com.quoteflow.ai.copilot.dto;

import com.quoteflow.ai.action.dto.ActionProposalSummaryDto;

import java.util.List;

public record BusinessCopilotResponse(
		String answer,
		List<BusinessCopilotReference> references,
		List<String> warnings,
		ActionProposalSummaryDto actionProposal
) {
	public BusinessCopilotResponse {
		references = references == null ? List.of() : List.copyOf(references);
		warnings = warnings == null ? List.of() : List.copyOf(warnings);
	}

	public BusinessCopilotResponse(String answer, List<BusinessCopilotReference> references, List<String> warnings) {
		this(answer, references, warnings, null);
	}
}
