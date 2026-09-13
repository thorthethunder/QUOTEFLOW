package com.quoteflow.subscription.dto;

import com.quoteflow.billing.BillingInterval;
import com.quoteflow.subscription.PlanDefinition;
import com.quoteflow.subscription.PlanId;

import java.util.List;

public record PlanCatalogItemDto(
		PlanId id,
		String displayName,
		Integer activeCustomerLimit,
		Integer quotationsPerMonth,
		Integer invoicesPerMonth,
		boolean removeQuoteFlowBranding,
		boolean multiUser,
		String monthlyPriceDisplay,
		String yearlyPriceDisplay,
		boolean billingAvailable,
		List<BillingInterval> billingIntervals
) {
	public static PlanCatalogItemDto from(PlanDefinition def, boolean billingAvailable, List<BillingInterval> intervals) {
		return new PlanCatalogItemDto(
				def.id(),
				def.displayName(),
				def.activeCustomerLimit(),
				def.quotationsPerMonth(),
				def.invoicesPerMonth(),
				def.removeQuoteFlowBranding(),
				def.multiUser(),
				def.monthlyPriceDisplay(),
				def.yearlyPriceDisplay(),
				billingAvailable,
				intervals);
	}
}
