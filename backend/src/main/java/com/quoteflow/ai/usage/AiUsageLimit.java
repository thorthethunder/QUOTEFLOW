package com.quoteflow.ai.usage;

import com.quoteflow.ai.provider.AiFeature;
import com.quoteflow.subscription.PlanId;

public record AiUsageLimit(
		PlanId plan,
		AiFeature feature,
		boolean enabled,
		int monthlyAllowance
) {
	public boolean unlimited() {
		return monthlyAllowance < 0;
	}
}
