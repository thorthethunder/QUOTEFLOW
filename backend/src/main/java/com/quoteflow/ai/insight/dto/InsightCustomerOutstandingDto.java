package com.quoteflow.ai.insight.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record InsightCustomerOutstandingDto(
		UUID customerId,
		String customerDisplayName,
		String currency,
		BigDecimal outstandingAmount,
		BigDecimal currencyOutstandingTotal,
		BigDecimal concentrationPercent
) {
}
