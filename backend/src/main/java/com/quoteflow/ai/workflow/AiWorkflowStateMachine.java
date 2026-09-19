package com.quoteflow.ai.workflow;

import com.quoteflow.common.api.DomainApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

@Component
public class AiWorkflowStateMachine {

	private static final Map<AiWorkflowStatus, Set<AiWorkflowStatus>> WORKFLOW_TRANSITIONS = Map.of(
			AiWorkflowStatus.CREATED, Set.of(AiWorkflowStatus.RUNNING, AiWorkflowStatus.CANCELLED, AiWorkflowStatus.EXPIRED),
			AiWorkflowStatus.RUNNING, Set.of(AiWorkflowStatus.WAITING_FOR_APPROVAL, AiWorkflowStatus.COMPLETED,
					AiWorkflowStatus.COMPLETED_WITH_PARTIAL_RESULTS, AiWorkflowStatus.FAILED,
					AiWorkflowStatus.CANCELLED, AiWorkflowStatus.EXPIRED),
			AiWorkflowStatus.WAITING_FOR_APPROVAL, Set.of(AiWorkflowStatus.RUNNING, AiWorkflowStatus.COMPLETED,
					AiWorkflowStatus.COMPLETED_WITH_PARTIAL_RESULTS, AiWorkflowStatus.FAILED,
					AiWorkflowStatus.CANCELLED, AiWorkflowStatus.EXPIRED),
			AiWorkflowStatus.COMPLETED, Set.of(),
			AiWorkflowStatus.COMPLETED_WITH_PARTIAL_RESULTS, Set.of(),
			AiWorkflowStatus.FAILED, Set.of(),
			AiWorkflowStatus.CANCELLED, Set.of(),
			AiWorkflowStatus.EXPIRED, Set.of());

	private static final Map<AiWorkflowStepStatus, Set<AiWorkflowStepStatus>> STEP_TRANSITIONS = Map.of(
			AiWorkflowStepStatus.PENDING, Set.of(AiWorkflowStepStatus.RUNNING, AiWorkflowStepStatus.CANCELLED,
					AiWorkflowStepStatus.SKIPPED, AiWorkflowStepStatus.EXPIRED),
			AiWorkflowStepStatus.RUNNING, Set.of(AiWorkflowStepStatus.WAITING_FOR_APPROVAL,
					AiWorkflowStepStatus.COMPLETED, AiWorkflowStepStatus.FAILED, AiWorkflowStepStatus.CANCELLED,
					AiWorkflowStepStatus.EXPIRED),
			AiWorkflowStepStatus.WAITING_FOR_APPROVAL, Set.of(AiWorkflowStepStatus.COMPLETED,
					AiWorkflowStepStatus.FAILED, AiWorkflowStepStatus.CANCELLED, AiWorkflowStepStatus.EXPIRED),
			AiWorkflowStepStatus.COMPLETED, Set.of(),
			AiWorkflowStepStatus.FAILED, Set.of(),
			AiWorkflowStepStatus.CANCELLED, Set.of(),
			AiWorkflowStepStatus.SKIPPED, Set.of(),
			AiWorkflowStepStatus.EXPIRED, Set.of());

	public void transition(AiWorkflow workflow, AiWorkflowStatus to) {
		AiWorkflowStatus from = workflow.getStatus();
		if (from == to) {
			return;
		}
		if (!WORKFLOW_TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
			throw invalid("workflow", from.name(), to.name());
		}
		workflow.setStatus(to);
		if (isTerminal(to)) {
			workflow.setCompletedAt(Instant.now());
		}
	}

	public void transition(AiWorkflowStep step, AiWorkflowStepStatus to) {
		AiWorkflowStepStatus from = step.getStatus();
		if (from == to) {
			return;
		}
		if (!STEP_TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
			throw invalid("workflow step", from.name(), to.name());
		}
		Instant now = Instant.now();
		if (from == AiWorkflowStepStatus.PENDING && to == AiWorkflowStepStatus.RUNNING) {
			step.setStartedAt(now);
		}
		step.setStatus(to);
		if (isTerminal(to)) {
			step.setCompletedAt(now);
		}
	}

	private static boolean isTerminal(AiWorkflowStatus status) {
		return status == AiWorkflowStatus.COMPLETED
				|| status == AiWorkflowStatus.COMPLETED_WITH_PARTIAL_RESULTS
				|| status == AiWorkflowStatus.FAILED
				|| status == AiWorkflowStatus.CANCELLED
				|| status == AiWorkflowStatus.EXPIRED;
	}

	private static boolean isTerminal(AiWorkflowStepStatus status) {
		return status == AiWorkflowStepStatus.COMPLETED
				|| status == AiWorkflowStepStatus.FAILED
				|| status == AiWorkflowStepStatus.CANCELLED
				|| status == AiWorkflowStepStatus.SKIPPED
				|| status == AiWorkflowStepStatus.EXPIRED;
	}

	private static DomainApiException invalid(String subject, String from, String to) {
		return new DomainApiException(HttpStatus.CONFLICT, "INVALID_WORKFLOW_TRANSITION",
				"Cannot transition " + subject + " from " + from + " to " + to);
	}
}
