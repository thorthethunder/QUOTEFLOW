package com.quoteflow.ai.action.payload;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Canonical reviewed payload for payment_reminder.send.
 * Recipient and financial fields are server-authoritative at preparation;
 * confirmation revalidates they still match current business state.
 */
public record PaymentReminderSendPayload(
		UUID invoiceId,
		UUID customerId,
		String invoiceNumber,
		String customerDisplayName,
		String recipientEmail,
		String currency,
		BigDecimal outstandingAmount,
		String paymentState,
		String subject,
		String bodyPlainText
) {
}
