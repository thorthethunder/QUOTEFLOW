package com.quoteflow.notification.email;

public interface EmailProvider {
	String providerName();

	EmailDeliveryResult send(EmailMessage message);
}
