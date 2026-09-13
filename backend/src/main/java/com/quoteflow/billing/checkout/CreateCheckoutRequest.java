package com.quoteflow.billing.checkout;

import com.quoteflow.billing.BillingInterval;
import com.quoteflow.subscription.PlanId;
import jakarta.validation.constraints.NotNull;

public record CreateCheckoutRequest(
		@NotNull PlanId plan,
		@NotNull BillingInterval billingInterval
) {
}
