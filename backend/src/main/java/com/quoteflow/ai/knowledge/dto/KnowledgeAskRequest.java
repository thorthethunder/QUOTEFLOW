package com.quoteflow.ai.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KnowledgeAskRequest(
		@NotBlank @Size(max = 1000) String question
) {
}
