package com.quoteflow.ai.workflow.dto;

import com.quoteflow.ai.workflow.AiWorkflowStatus;
import com.quoteflow.ai.workflow.AiWorkflowType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AiWorkflowResponse(
		UUID id,
		AiWorkflowType workflowType,
		AiWorkflowStatus status,
		String goal,
		Instant createdAt,
		Instant updatedAt,
		Instant startedAt,
		Instant completedAt,
		Instant expiresAt,
		List<AiWorkflowStepDto> steps,
		WorkflowSummary summary
) {
	public record WorkflowSummary(
			int totalSteps,
			int pendingApprovals,
			int executedActions,
			int failedActions,
			int cancelledActions,
			int expiredActions,
			boolean partialResults
	) {
	}
}
