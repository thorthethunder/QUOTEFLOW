package com.quoteflow.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quoteflow.common.api.DomainApiException;
import com.quoteflow.notification.email.EmailProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Durable outbox enqueue with explicit RETRY vs RESEND semantics:
 * <ul>
 *   <li>PENDING/SENDING — always reuse (accidental double-submit / concurrent)</li>
 *   <li>FAILED + same idempotency (no explicitResend) — RETRY: requeue same row</li>
 *   <li>SENT + same idempotency (no explicitResend) — reuse; no duplicate send</li>
 *   <li>explicitResend — create a new notification (historical rows remain)</li>
 * </ul>
 */
@Service
public class NotificationService {

	private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

	private final NotificationRepository notificationRepository;
	private final NotificationConflictLookup conflictLookup;
	private final EmailProperties emailProperties;
	private final ObjectMapper objectMapper;
	private final TransactionTemplate transactionTemplate;

	public NotificationService(
			NotificationRepository notificationRepository,
			NotificationConflictLookup conflictLookup,
			EmailProperties emailProperties,
			ObjectMapper objectMapper,
			TransactionTemplate transactionTemplate) {
		this.notificationRepository = notificationRepository;
		this.conflictLookup = conflictLookup;
		this.emailProperties = emailProperties;
		this.objectMapper = objectMapper;
		this.transactionTemplate = transactionTemplate;
	}

	/**
	 * Enqueue outside a poisoned-TX catch: unique races are resolved via
	 * {@link NotificationConflictLookup} in a fresh transaction.
	 */
	public Notification enqueue(EnqueueCommand command) {
		try {
			return transactionTemplate.execute(status -> enqueueInTransaction(command));
		} catch (DataIntegrityViolationException ex) {
			log.info("notification.enqueue_race type={} referenceId={}", command.type(), command.referenceId());
			return conflictLookup
					.findAfterEnqueueRace(
							command.businessId(),
							command.type(),
							command.referenceId(),
							command.idempotencyKey())
					.orElseThrow(() -> ex);
		}
	}

	private Notification enqueueInTransaction(EnqueueCommand command) {
		Optional<Notification> active = notificationRepository
				.findFirstByBusinessIdAndTypeAndReferenceIdAndStatusInOrderByCreatedAtDesc(
						command.businessId(),
						command.type(),
						command.referenceId(),
						List.of(NotificationStatus.PENDING, NotificationStatus.SENDING));
		if (active.isPresent()) {
			log.info("notification.deduped id={} type={} reason=in_flight", active.get().getId(), command.type());
			return active.get();
		}

		if (!command.explicitResend() && command.idempotencyKey() != null) {
			Optional<Notification> byKey = notificationRepository
					.findByBusinessIdAndIdempotencyKey(command.businessId(), command.idempotencyKey());
			if (byKey.isPresent()) {
				Notification existing = byKey.get();
				if (existing.getStatus() == NotificationStatus.FAILED) {
					existing.requeueForRetry();
					Notification saved = notificationRepository.saveAndFlush(existing);
					log.info("notification.retry id={} type={}", saved.getId(), saved.getType());
					return saved;
				}
				log.info("notification.deduped id={} type={} reason=idempotency status={}",
						existing.getId(), existing.getType(), existing.getStatus());
				return existing;
			}
		}

		String varsJson;
		try {
			varsJson = objectMapper.writeValueAsString(command.templateVars());
		} catch (Exception ex) {
			throw new DomainApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unable to queue notification");
		}

		String idempotencyKey = command.idempotencyKey();
		if (command.explicitResend() && idempotencyKey == null) {
			idempotencyKey = "resend:" + command.type().name().toLowerCase() + ":"
					+ command.referenceId() + ":" + UUID.randomUUID();
		}

		Notification notification = new Notification(
				command.businessId(),
				command.type(),
				command.recipientEmail(),
				command.recipientDisplayName(),
				command.subject(),
				command.templateKey(),
				varsJson,
				command.referenceType(),
				command.referenceId(),
				command.replyToEmail(),
				command.includePdfAttachment(),
				idempotencyKey,
				emailProperties.getMaxAttempts());

		Notification saved = notificationRepository.saveAndFlush(notification);
		log.info(
				"notification.created id={} type={} businessId={} referenceType={} resend={}",
				saved.getId(),
				saved.getType(),
				saved.getBusinessId(),
				saved.getReferenceType(),
				command.explicitResend());
		return saved;
	}

	@Transactional(readOnly = true)
	public NotificationResponse get(UUID businessId, UUID notificationId) {
		Notification notification = notificationRepository.findByIdAndBusinessId(notificationId, businessId)
				.orElseThrow(() -> new DomainApiException(
						HttpStatus.NOT_FOUND, "NOT_FOUND", "Notification not found"));
		return NotificationResponse.from(notification);
	}

	@Transactional(readOnly = true)
	public List<NotificationResponse> listForReference(
			UUID businessId,
			NotificationReferenceType referenceType,
			UUID referenceId) {
		return notificationRepository
				.findTop20ByBusinessIdAndReferenceTypeAndReferenceIdOrderByCreatedAtDesc(
						businessId, referenceType, referenceId)
				.stream()
				.map(NotificationResponse::from)
				.toList();
	}

	public record EnqueueCommand(
			UUID businessId,
			NotificationType type,
			String recipientEmail,
			String recipientDisplayName,
			String subject,
			String templateKey,
			Map<String, String> templateVars,
			NotificationReferenceType referenceType,
			UUID referenceId,
			String replyToEmail,
			boolean includePdfAttachment,
			String idempotencyKey,
			boolean explicitResend
	) {
	}
}
