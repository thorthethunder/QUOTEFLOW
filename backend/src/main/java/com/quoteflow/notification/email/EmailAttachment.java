package com.quoteflow.notification.email;

public record EmailAttachment(
		String filename,
		String contentType,
		byte[] content
) {
}
