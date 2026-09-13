package com.quoteflow.reporting.dto;

import java.util.List;

/**
 * Invoice metrics for SENT invoices with issue_date in the selected period.
 * Outstanding is current balance due for those invoices (not historical as-of).
 */
public record InvoiceMetricsDto(
		long sentCount,
		long draftCount,
		long cancelledCount,
		long unpaidCount,
		long partiallyPaidCount,
		long paidCount,
		List<MoneyByCurrency> invoicedAmountByCurrency,
		List<MoneyByCurrency> outstandingAmountByCurrency
) {
}
