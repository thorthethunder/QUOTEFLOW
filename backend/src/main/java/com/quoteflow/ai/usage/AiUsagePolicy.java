package com.quoteflow.ai.usage;

import com.quoteflow.ai.provider.AiFeature;
import com.quoteflow.subscription.PlanId;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

@Component
public class AiUsagePolicy {

	static final int HARD_MAX_MONTHLY_ALLOWANCE = 10_000;

	private final Map<PlanId, Map<AiFeature, Integer>> allowances = new EnumMap<>(PlanId.class);

	public AiUsagePolicy() {
		allowances.put(PlanId.FREE, Map.of(
				AiFeature.QUOTE_DRAFT, 20,
				AiFeature.BUSINESS_COPILOT, 10,
				AiFeature.REPORTING_INSIGHT, 10,
				AiFeature.PAYMENT_REMINDER, 10,
				AiFeature.KNOWLEDGE_INGESTION, 5,
				AiFeature.KNOWLEDGE_QUERY, 20,
				AiFeature.AGENT_WORKFLOW, 3));
		allowances.put(PlanId.PRO, Map.of(
				AiFeature.QUOTE_DRAFT, 100,
				AiFeature.BUSINESS_COPILOT, 100,
				AiFeature.REPORTING_INSIGHT, 100,
				AiFeature.PAYMENT_REMINDER, 100,
				AiFeature.KNOWLEDGE_INGESTION, 50,
				AiFeature.KNOWLEDGE_QUERY, 250,
				AiFeature.AGENT_WORKFLOW, 25));
		allowances.put(PlanId.BUSINESS, Map.of(
				AiFeature.QUOTE_DRAFT, 500,
				AiFeature.BUSINESS_COPILOT, 500,
				AiFeature.REPORTING_INSIGHT, 500,
				AiFeature.PAYMENT_REMINDER, 500,
				AiFeature.KNOWLEDGE_INGESTION, 200,
				AiFeature.KNOWLEDGE_QUERY, 1000,
				AiFeature.AGENT_WORKFLOW, 100));
	}

	public AiUsageLimit limit(PlanId plan, AiFeature feature) {
		Integer value = allowances.getOrDefault(plan, Map.of()).get(feature);
		if (value == null) {
			return new AiUsageLimit(plan, feature, false, 0);
		}
		return new AiUsageLimit(plan, feature, true, Math.min(value, HARD_MAX_MONTHLY_ALLOWANCE));
	}
}
