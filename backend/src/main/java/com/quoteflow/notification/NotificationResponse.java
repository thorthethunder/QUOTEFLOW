package com.quoteflow.notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
		UUID id,
		NotificationType type,
		NotificationChannel channel,
		NotificationStatus status,
		String recipientMasked,
		String subject,
		NotificationReferenceType referenceType,
		UUID referenceId,
		int attemptCount,
		String lastErrorCode,
		Instant createdAt,
		Instant sentAt
) {
	public static NotificationResponse from(Notification n) {
		return new NotificationResponse(
				n.getId(),
				n.getType(),
				n.getChannel(),
				n.getStatus(),
				maskEmail(n.getRecipientEmail()),
				n.getSubject(),
				n.getReferenceType(),
				n.getReferenceId(),
				n.getAttemptCount(),
				n.getLastErrorCode(),
				n.getCreatedAt(),
				n.getSentAt());
	}

	static String maskEmail(String email) {
		if (email == null || !email.contains("@")) {
			return "***";
		}
		String local = email.substring(0, email.indexOf('@'));
		String domain = email.substring(email.indexOf('@') + 1);
		String maskedLocal = local.length() <= 1 ? "*" : local.charAt(0) + "***";
		return maskedLocal + "@" + domain;
	}
}
