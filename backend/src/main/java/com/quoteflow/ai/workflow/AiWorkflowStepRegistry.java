package com.quoteflow.ai.workflow;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

@Component
public class AiWorkflowStepRegistry {

	private final Map<AiWorkflowStepType, AiWorkflowStepClassification> steps =
			new EnumMap<>(AiWorkflowStepType.class);

	public AiWorkflowStepRegistry() {
		steps.put(AiWorkflowStepType.READ_OUTSTANDING_INVOICES, AiWorkflowStepClassification.READ_ONLY);
		steps.put(AiWorkflowStepType.PREPARE_PAYMENT_REMINDER, AiWorkflowStepClassification.ACTION_REQUIRES_APPROVAL);
		steps.put(AiWorkflowStepType.OBSERVE_ACTION_RESULT, AiWorkflowStepClassification.READ_ONLY);
	}

	public AiWorkflowStepClassification classification(AiWorkflowStepType type) {
		return steps.getOrDefault(type, AiWorkflowStepClassification.FORBIDDEN);
	}

	public boolean isAllowed(AiWorkflowStepType type) {
		return steps.containsKey(type) && classification(type) != AiWorkflowStepClassification.FORBIDDEN;
	}
}
