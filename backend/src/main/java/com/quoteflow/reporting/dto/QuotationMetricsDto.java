package com.quoteflow.reporting.dto;

import java.util.List;

public record QuotationMetricsDto(
		long draftCount,
		long sentCount,
		long cancelledCount,
		long convertedCount,
		List<MoneyByCurrency> quotedAmountByCurrency
) {
}
