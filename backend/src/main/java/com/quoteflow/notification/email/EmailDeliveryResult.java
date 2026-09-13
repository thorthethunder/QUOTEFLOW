package com.quoteflow.notification.email;

public record EmailDeliveryResult(
		boolean accepted,
		String providerMessageId,
		String safeErrorCode,
		boolean retryable
) {
	public static EmailDeliveryResult success(String providerMessageId) {
		return new EmailDeliveryResult(true, providerMessageId, null, false);
	}

	public static EmailDeliveryResult failure(String safeErrorCode, boolean retryable) {
		return new EmailDeliveryResult(false, null, safeErrorCode, retryable);
	}
}
