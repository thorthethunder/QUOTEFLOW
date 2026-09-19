package com.quoteflow.ai.workflow;

public enum AiWorkflowStepStatus {
	PENDING,
	RUNNING,
	WAITING_FOR_APPROVAL,
	COMPLETED,
	FAILED,
	CANCELLED,
	SKIPPED,
	EXPIRED
}
