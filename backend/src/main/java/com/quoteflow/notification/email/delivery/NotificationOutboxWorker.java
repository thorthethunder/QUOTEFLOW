package com.quoteflow.notification.email.delivery;

import com.quoteflow.notification.email.EmailProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Claims PENDING (and stale SENDING) notifications with SKIP LOCKED and delivers them.
 * Provider outages only degrade email — core business remains available.
 */
@Component
@ConditionalOnProperty(prefix = "quoteflow.email", name = "worker-enabled", havingValue = "true", matchIfMissing = true)
public class NotificationOutboxWorker {

	private static final Logger log = LoggerFactory.getLogger(NotificationOutboxWorker.class);

	private final NotificationDeliveryService deliveryService;
	private final EmailProperties emailProperties;
	private volatile boolean shuttingDown;

	public NotificationOutboxWorker(NotificationDeliveryService deliveryService, EmailProperties emailProperties) {
		this.deliveryService = deliveryService;
		this.emailProperties = emailProperties;
	}

	@PreDestroy
	void onShutdown() {
		shuttingDown = true;
	}

	@Scheduled(fixedDelayString = "${quoteflow.email.worker-interval:5s}")
	public void poll() {
		if (shuttingDown || !emailProperties.isWorkerEnabled()) {
			return;
		}
		List<UUID> claimed = deliveryService.claimBatch();
		for (UUID id : claimed) {
			if (shuttingDown) {
				return;
			}
			try {
				deliveryService.deliver(id);
			} catch (Exception ex) {
				log.warn("notification.worker.deliver_error id={}", id);
			}
		}
	}
}
