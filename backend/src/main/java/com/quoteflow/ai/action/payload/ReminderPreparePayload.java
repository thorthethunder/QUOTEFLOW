package com.quoteflow.ai.action.payload;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Canonical stored payload for reminder.prepare. Does NOT send email on confirm.
 */
public record ReminderPreparePayload(
		UUID invoiceId,
		String invoiceNumber,
		String customerDisplayName,
		String currency,
		BigDecimal balanceDue,
		String dueDate,
		String subject,
		String bodyPlainText
) {
}
