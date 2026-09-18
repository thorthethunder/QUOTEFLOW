package com.quoteflow.ai.action.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Manual / invoice-UI entry to prepare a payment reminder send proposal (no LLM required).
 */
public record PreparePaymentReminderRequest(
		@NotNull UUID invoiceId,
		@NotBlank @Size(max = 200) String subject,
		@NotBlank @Size(max = 2000) String bodyPlainText
) {
}
