package com.quoteflow.subscription.dto;

import com.quoteflow.billing.BillingInterval;
import com.quoteflow.subscription.PlanId;
import com.quoteflow.subscription.SubscriptionStatus;

import java.time.Instant;
import java.util.List;

public record EntitlementResponse(
		PlanId plan,
		String planDisplayName,
		SubscriptionStatus status,
		EntitlementPeriodDto period,
		EntitlementLimitsDto limits,
		FeatureFlagsDto features,
		boolean billingCheckoutAvailable,
		BillingInterval billingInterval,
		Instant billingPeriodStart,
		Instant billingPeriodEnd,
		boolean cancelAtPeriodEnd,
		String billingProvider,
		String providerStatus,
		List<BillingInterval> availableBillingIntervals
) {
}
