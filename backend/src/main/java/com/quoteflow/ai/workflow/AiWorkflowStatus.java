package com.quoteflow.ai.workflow;

public enum AiWorkflowStatus {
	CREATED,
	RUNNING,
	WAITING_FOR_APPROVAL,
	COMPLETED,
	COMPLETED_WITH_PARTIAL_RESULTS,
	FAILED,
	CANCELLED,
	EXPIRED
}
