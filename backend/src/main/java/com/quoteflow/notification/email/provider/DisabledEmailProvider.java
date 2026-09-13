package com.quoteflow.notification.email.provider;

import com.quoteflow.notification.email.EmailDeliveryResult;
import com.quoteflow.notification.email.EmailMessage;
import com.quoteflow.notification.email.EmailProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Explicitly disabled email — never pretends a message was accepted.
 * Preferred for hosted staging when Resend is not configured.
 */
@Component
@ConditionalOnProperty(prefix = "quoteflow.email", name = "provider", havingValue = "DISABLED")
public class DisabledEmailProvider implements EmailProvider {

	private static final Logger log = LoggerFactory.getLogger(DisabledEmailProvider.class);

	@Override
	public String providerName() {
		return "DISABLED";
	}

	@Override
	public EmailDeliveryResult send(EmailMessage message) {
		log.info("email.disabled rejected attempt (EMAIL_PROVIDER=DISABLED)");
		return EmailDeliveryResult.failure("EMAIL_DISABLED", false);
	}
}
