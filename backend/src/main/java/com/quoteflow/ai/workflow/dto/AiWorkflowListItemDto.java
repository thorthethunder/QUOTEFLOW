package com.quoteflow.ai.workflow.dto;

import com.quoteflow.ai.workflow.AiWorkflowStatus;
import com.quoteflow.ai.workflow.AiWorkflowType;

import java.time.Instant;
import java.util.UUID;

public record AiWorkflowListItemDto(
		UUID id,
		AiWorkflowType workflowType,
		AiWorkflowStatus status,
		String goal,
		Instant updatedAt,
		Instant expiresAt
) {
}
