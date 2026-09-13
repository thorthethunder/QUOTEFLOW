package com.quoteflow.reporting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RecentPaymentDto(
		UUID id,
		String receiptNumber,
		String invoiceNumber,
		UUID invoiceId,
		LocalDate paymentDate,
		String paymentMethod,
		String currency,
		BigDecimal amount
) {
}
