package com.quoteflow.invoice;

import jakarta.validation.constraints.Size;

public record SendInvoiceReminderRequest(
		ReminderTone tone,
		@Size(max = 500) String message,
		Boolean resend
) {
}
