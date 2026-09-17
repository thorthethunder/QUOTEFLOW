package com.quoteflow.ai.tool.payment;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record PaymentStatusInput(
		@Size(max = 40)
		String invoiceNumber,

		@Size(max = 120)
		String customerQuery,

		@Size(max = 20)
		String paymentState,

		@Min(1)
		@Max(20)
		Integer limit
) {
	public PaymentStatusInput {
		if (limit == null) {
			limit = 10;
		}
	}
}
