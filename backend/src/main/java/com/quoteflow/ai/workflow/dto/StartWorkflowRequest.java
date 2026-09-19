package com.quoteflow.ai.workflow.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StartWorkflowRequest(
		@NotBlank
		String workflowType,
		@Size(max = 1000)
		String goal,
		@Min(1)
		@Max(5)
		Integer maxItems,
		@Size(max = 120)
		String idempotencyKey
) {
}
