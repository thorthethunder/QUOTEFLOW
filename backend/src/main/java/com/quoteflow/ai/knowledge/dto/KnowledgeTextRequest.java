package com.quoteflow.ai.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KnowledgeTextRequest(
		@NotBlank @Size(max = 140) String title,
		@NotBlank String text
) {
}
