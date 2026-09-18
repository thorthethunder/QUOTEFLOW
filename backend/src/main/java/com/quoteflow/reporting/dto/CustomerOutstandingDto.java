package com.quoteflow.reporting.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CustomerOutstandingDto(
		UUID customerId,
		String customerDisplayName,
		String currency,
		BigDecimal outstandingAmount,
		BigDecimal currencyOutstandingTotal,
		BigDecimal concentrationPercent
) {
}
