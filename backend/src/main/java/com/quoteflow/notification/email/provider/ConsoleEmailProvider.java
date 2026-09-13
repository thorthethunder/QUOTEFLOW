package com.quoteflow.notification.email.provider;

import com.quoteflow.notification.email.EmailDeliveryResult;
import com.quoteflow.notification.email.EmailMessage;
import com.quoteflow.notification.email.EmailProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Local/dev provider — never contacts the internet. Logs safe metadata only.
 */
@Component
@ConditionalOnProperty(prefix = "quoteflow.email", name = "provider", havingValue = "CONSOLE", matchIfMissing = true)
public class ConsoleEmailProvider implements EmailProvider {

	private static final Logger log = LoggerFactory.getLogger(ConsoleEmailProvider.class);
	private final AtomicInteger sent = new AtomicInteger();

	@Override
	public String providerName() {
		return "CONSOLE";
	}

	@Override
	public EmailDeliveryResult send(EmailMessage message) {
		String messageId = "console_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
		sent.incrementAndGet();
		log.info(
				"email.console.accepted toDomain={} subjectLen={} attachmentCount={} messageId={}",
				maskDomain(message.toEmail()),
				message.subject() == null ? 0 : message.subject().length(),
				message.attachments() == null ? 0 : message.attachments().size(),
				messageId);
		return EmailDeliveryResult.success(messageId);
	}

	public int sentCount() {
		return sent.get();
	}

	private static String maskDomain(String email) {
		if (email == null || !email.contains("@")) {
			return "***";
		}
		return "***@" + email.substring(email.indexOf('@') + 1);
	}
}
