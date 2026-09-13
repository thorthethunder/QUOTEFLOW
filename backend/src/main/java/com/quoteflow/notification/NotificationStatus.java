package com.quoteflow.notification;

/**
 * Local outbox status. SENT means the provider accepted the API request — not inbox delivery.
 */
public enum NotificationStatus {
	PENDING,
	SENDING,
	SENT,
	FAILED,
	CANCELLED
}
