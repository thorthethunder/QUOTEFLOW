package com.quoteflow.billing.checkout;

import com.quoteflow.billing.BillingInterval;
import com.quoteflow.subscription.PlanId;
import com.quoteflow.subscription.SubscriptionStatus;

public record VerifyCheckoutResponse(
		PlanId plan,
		SubscriptionStatus status,
		String providerStatus,
		BillingInterval billingInterval,
		boolean activated
) {
}
