package com.quoteflow.billing.provider;

import java.time.Instant;
import java.util.Map;

/**
 * Provider-agnostic subscription snapshot. Core billing never imports Razorpay SDK types.
 */
public record ProviderSubscription(
		String providerSubscriptionId,
		String providerCustomerId,
		String providerPlanId,
		String status,
		Instant currentPeriodStart,
		Instant currentPeriodEnd,
		boolean cancelAtCycleEnd,
		Instant endedAt,
		Instant providerUpdatedAt,
		Map<String, String> notes
) {
}
