package com.quoteflow.notification.email;

import java.util.List;

public record EmailMessage(
		String toEmail,
		String toDisplayName,
		String subject,
		String htmlBody,
		String textBody,
		String replyToEmail,
		String idempotencyKey,
		List<EmailAttachment> attachments
) {
}
