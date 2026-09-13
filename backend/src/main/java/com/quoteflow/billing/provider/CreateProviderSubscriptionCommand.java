package com.quoteflow.billing.provider;

import java.util.Map;

public record CreateProviderSubscriptionCommand(
		String providerPlanId,
		int totalCount,
		Map<String, String> notes
) {
}
