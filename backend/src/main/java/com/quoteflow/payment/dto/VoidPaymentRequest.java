package com.quoteflow.payment.dto;

import jakarta.validation.constraints.Size;

public record VoidPaymentRequest(
		@Size(max = 500) String reason
) {
}
