package com.quoteflow.reporting.dto;

import java.time.LocalDate;
import java.util.List;

public record DashboardSummaryResponse(
		LocalDate from,
		LocalDate to,
		String timezone,
		String defaultPeriodLabel,
		CustomerMetricsDto customers,
		QuotationMetricsDto quotations,
		InvoiceMetricsDto invoices,
		PaymentMetricsDto payments,
		List<CollectionsSeriesPoint> collectionsSeries,
		List<RecentInvoiceDto> recentInvoices,
		List<RecentPaymentDto> recentPayments
) {
}
