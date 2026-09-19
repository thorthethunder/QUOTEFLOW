package com.quoteflow.ai.workflow;

import com.quoteflow.common.api.DomainApiException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiWorkflowStateMachineTest {

	private final AiWorkflowStateMachine stateMachine = new AiWorkflowStateMachine();

	@Test
	void rejectsInvalidWorkflowTransition() {
		AiWorkflow workflow = new AiWorkflow(
				UUID.randomUUID(),
				UUID.randomUUID(),
				AiWorkflowType.PAYMENT_FOLLOW_UP,
				"goal",
				null,
				Instant.now(),
				Instant.now().plusSeconds(60));
		stateMachine.transition(workflow, AiWorkflowStatus.CANCELLED);

		assertThatThrownBy(() -> stateMachine.transition(workflow, AiWorkflowStatus.RUNNING))
				.isInstanceOf(DomainApiException.class)
				.hasMessageContaining("Cannot transition workflow");
	}

	@Test
	void rejectsInvalidStepTransition() {
		AiWorkflowStep step = new AiWorkflowStep(
				UUID.randomUUID(),
				1,
				AiWorkflowStepType.READ_OUTSTANDING_INVOICES,
				AiWorkflowStepClassification.READ_ONLY,
				"{}");
		stateMachine.transition(step, AiWorkflowStepStatus.RUNNING);
		stateMachine.transition(step, AiWorkflowStepStatus.COMPLETED);

		assertThatThrownBy(() -> stateMachine.transition(step, AiWorkflowStepStatus.RUNNING))
				.isInstanceOf(DomainApiException.class)
				.hasMessageContaining("Cannot transition workflow step");
	}
}
