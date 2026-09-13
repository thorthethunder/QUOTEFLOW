package com.quoteflow.notification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lookups that must succeed after a unique-constraint failure poisoned the caller transaction
 * (PostgreSQL aborts the TX; same-connection queries would fail).
 */
@Service
public class NotificationConflictLookup {

	private final NotificationRepository notificationRepository;

	public NotificationConflictLookup(NotificationRepository notificationRepository) {
		this.notificationRepository = notificationRepository;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
	public Optional<Notification> findAfterEnqueueRace(
			UUID businessId,
			NotificationType type,
			UUID referenceId,
			String idempotencyKey) {
		Optional<Notification> active = notificationRepository
				.findFirstByBusinessIdAndTypeAndReferenceIdAndStatusInOrderByCreatedAtDesc(
						businessId,
						type,
						referenceId,
						List.of(NotificationStatus.PENDING, NotificationStatus.SENDING));
		if (active.isPresent()) {
			return active;
		}
		if (idempotencyKey != null) {
			return notificationRepository.findByBusinessIdAndIdempotencyKey(businessId, idempotencyKey);
		}
		return Optional.empty();
	}
}
