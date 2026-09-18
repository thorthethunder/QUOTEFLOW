package com.quoteflow.reporting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record OutstandingInvoiceDto(
		UUID id,
		String invoiceNumber,
		UUID customerId,
		String customerDisplayName,
		LocalDate issueDate,
		LocalDate dueDate,
		String currency,
		BigDecimal totalAmount,
		BigDecimal amountPaid,
		BigDecimal balanceDue,
		String paymentState
) {
}
