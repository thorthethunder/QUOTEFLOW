package com.quoteflow.subscription.dto;

public record EntitlementLimitsDto(
		UsageMeterDto activeCustomers,
		UsageMeterDto quotationsThisMonth,
		UsageMeterDto invoicesThisMonth
) {
}
