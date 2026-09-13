package com.quoteflow.notification.email.provider;

import com.quoteflow.notification.email.EmailDeliveryResult;
import com.quoteflow.notification.email.EmailMessage;
import com.quoteflow.notification.email.EmailProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Controllable in-memory provider for integration tests. No network I/O.
 */
@Component
@ConditionalOnProperty(prefix = "quoteflow.email", name = "provider", havingValue = "FAKE")
public class FakeEmailProvider implements EmailProvider {

	public enum Mode {
		SUCCESS,
		TRANSIENT_FAIL,
		PERMANENT_FAIL
	}

	private volatile Mode mode = Mode.SUCCESS;
	private final AtomicInteger sendCalls = new AtomicInteger();
	private final CopyOnWriteArrayList<EmailMessage> sent = new CopyOnWriteArrayList<>();
	private final AtomicInteger transientFailsBeforeSuccess = new AtomicInteger(0);

	@Override
	public String providerName() {
		return "FAKE";
	}

	@Override
	public EmailDeliveryResult send(EmailMessage message) {
		sendCalls.incrementAndGet();
		Mode current = mode;
		if (current == Mode.TRANSIENT_FAIL) {
			int remaining = transientFailsBeforeSuccess.get();
			if (remaining > 0) {
				transientFailsBeforeSuccess.decrementAndGet();
				return EmailDeliveryResult.failure("PROVIDER_TEMPORARY", true);
			}
			// fall through to success when countdown exhausted
		}
		if (current == Mode.PERMANENT_FAIL) {
			return EmailDeliveryResult.failure("PROVIDER_REJECTED", false);
		}
		String id = "fake_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
		sent.add(message);
		return EmailDeliveryResult.success(id);
	}

	public void reset() {
		mode = Mode.SUCCESS;
		sendCalls.set(0);
		sent.clear();
		transientFailsBeforeSuccess.set(0);
	}

	public void setMode(Mode mode) {
		this.mode = mode;
	}

	public void failTransientTimes(int times) {
		mode = Mode.TRANSIENT_FAIL;
		transientFailsBeforeSuccess.set(times);
	}

	public int sendCalls() {
		return sendCalls.get();
	}

	public List<EmailMessage> sentMessages() {
		return new ArrayList<>(sent);
	}
}
