package com.quoteflow.billing.checkout;

import com.quoteflow.billing.BillingInterval;
import com.quoteflow.subscription.PlanId;

public record CheckoutResponse(
		String provider,
		String keyId,
		String subscriptionId,
		PlanId plan,
		BillingInterval billingInterval
) {
}
