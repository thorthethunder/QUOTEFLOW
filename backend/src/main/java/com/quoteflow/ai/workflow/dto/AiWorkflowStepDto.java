package com.quoteflow.ai.workflow.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.quoteflow.ai.workflow.AiWorkflowStepClassification;
import com.quoteflow.ai.workflow.AiWorkflowStepStatus;
import com.quoteflow.ai.workflow.AiWorkflowStepType;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiWorkflowStepDto(
		UUID id,
		int stepNumber,
		AiWorkflowStepType stepType,
		AiWorkflowStepClassification classification,
		AiWorkflowStepStatus status,
		UUID actionProposalId,
		JsonNode input,
		JsonNode output,
		Instant startedAt,
		Instant completedAt,
		String failureCode
) {
}
