package com.quoteflow.payment.dto;

import java.util.List;

public record InvoicePaymentsResponse(
		PaymentSummaryResponse summary,
		List<PaymentResponse> payments
) {
}
