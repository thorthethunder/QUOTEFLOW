package com.quoteflow.reporting.dto;

import java.util.List;

/** Collections from RECORDED payments with payment_date in the selected period. */
public record PaymentMetricsDto(
		long recordedCount,
		List<MoneyByCurrency> collectedAmountByCurrency
) {
}
