package com.quoteflow.reporting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RecentInvoiceDto(
		UUID id,
		String invoiceNumber,
		String customerDisplayName,
		LocalDate issueDate,
		String status,
		String paymentState,
		String currency,
		BigDecimal totalAmount
) {
}
