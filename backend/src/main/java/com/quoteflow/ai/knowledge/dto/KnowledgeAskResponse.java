package com.quoteflow.ai.knowledge.dto;

import java.util.List;

public record KnowledgeAskResponse(
		String answer,
		boolean grounded,
		boolean aiNarrativeAvailable,
		List<KnowledgeSourceReference> sources,
		List<String> warnings
) {
}
