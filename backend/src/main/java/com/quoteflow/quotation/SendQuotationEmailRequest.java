package com.quoteflow.quotation;

import jakarta.validation.constraints.Size;

public record SendQuotationEmailRequest(
		@Size(max = 500) String message,
		Long version,
		Boolean resend
) {
}
