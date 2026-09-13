package com.quoteflow.notification.email.delivery;

import com.quoteflow.notification.Notification;
import com.quoteflow.notification.NotificationRepository;
import com.quoteflow.notification.NotificationStatus;
import com.quoteflow.notification.NotificationType;
import com.quoteflow.notification.email.EmailAttachment;
import com.quoteflow.notification.email.EmailDeliveryResult;
import com.quoteflow.notification.email.EmailMessage;
import com.quoteflow.notification.email.EmailProperties;
import com.quoteflow.notification.email.EmailProvider;
import com.quoteflow.notification.email.EmailProviderException;
import com.quoteflow.notification.email.template.EmailTemplateRenderer;
import com.quoteflow.quotation.pdf.QuotationPdfService;
import com.quoteflow.subscription.EntitlementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationDeliveryService {

	private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryService.class);
	private static final Duration[] BACKOFF = {
			Duration.ofMinutes(1),
			Duration.ofMinutes(5),
			Duration.ofMinutes(30)
	};

	private final NotificationRepository notificationRepository;
	private final EmailProvider emailProvider;
	private final EmailTemplateRenderer templateRenderer;
	private final EmailProperties emailProperties;
	private final EntitlementService entitlementService;
	private final QuotationPdfService quotationPdfService;

	public NotificationDeliveryService(
			NotificationRepository notificationRepository,
			EmailProvider emailProvider,
			EmailTemplateRenderer templateRenderer,
			EmailProperties emailProperties,
			EntitlementService entitlementService,
			QuotationPdfService quotationPdfService) {
		this.notificationRepository = notificationRepository;
		this.emailProvider = emailProvider;
		this.templateRenderer = templateRenderer;
		this.emailProperties = emailProperties;
		this.entitlementService = entitlementService;
		this.quotationPdfService = quotationPdfService;
	}

	@Transactional
	public List<UUID> claimBatch() {
		Instant now = Instant.now();
		Instant staleBefore = now.minus(emailProperties.getClaimLease());
		List<UUID> ids = notificationRepository.claimCandidateIds(
				now, staleBefore, emailProperties.getWorkerBatchSize());
		if (ids.isEmpty()) {
			return List.of();
		}
		notificationRepository.markSending(ids, now, staleBefore);
		return ids;
	}

	@Transactional
	public void deliver(UUID notificationId) {
		Notification notification = notificationRepository.findById(notificationId).orElse(null);
		if (notification == null) {
			return;
		}
		Instant now = Instant.now();
		Instant staleBefore = now.minus(emailProperties.getClaimLease());
		if (notification.getStatus() == NotificationStatus.PENDING
				|| (notification.getStatus() == NotificationStatus.SENDING
				&& notification.getUpdatedAt().isBefore(staleBefore))) {
			int claimed = notificationRepository.claimOne(notificationId, now, staleBefore);
			if (claimed == 0) {
				return;
			}
			notification = notificationRepository.findById(notificationId).orElse(null);
			if (notification == null) {
				return;
			}
		}
		if (notification.getStatus() != NotificationStatus.SENDING) {
			return;
		}
		deliverSending(notification);
	}

	/**
	 * Immediate dispatch helper for API after enqueue (tests/local). Worker uses claimBatch + deliver.
	 */
	@Transactional
	public void deliverClaimedOrPending(UUID notificationId) {
		deliver(notificationId);
	}

	private void deliverSending(Notification notification) {
		UUID notificationId = notification.getId();
		try {
			boolean branding = entitlementService.showQuoteFlowBranding(notification.getBusinessId());
			EmailTemplateRenderer.RenderedEmail rendered = templateRenderer.render(
					notification.getTemplateKey(),
					notification.getTemplateVarsJson(),
					branding);

			List<EmailAttachment> attachments = new ArrayList<>();
			if (notification.isIncludePdfAttachment()
					&& notification.getType() == NotificationType.QUOTATION_EMAIL) {
				QuotationPdfService.GeneratedPdf pdf = quotationPdfService.generateForBusiness(
						notification.getBusinessId(),
						notification.getReferenceId());
				if (pdf.content().length > emailProperties.getMaxPdfAttachmentBytes()) {
					failPermanent(notification, "ATTACHMENT_TOO_LARGE");
					return;
				}
				attachments.add(new EmailAttachment(pdf.filename(), "application/pdf", pdf.content()));
			}

			EmailMessage message = new EmailMessage(
					notification.getRecipientEmail(),
					notification.getRecipientDisplayName(),
					rendered.subject(),
					rendered.htmlBody(),
					rendered.textBody(),
					notification.getReplyToEmail(),
					notification.getIdempotencyKey() != null
							? notification.getIdempotencyKey()
							: notification.getId().toString(),
					attachments);

			EmailDeliveryResult result = emailProvider.send(message);
			notification.setProvider(emailProvider.providerName());
			if (result.accepted()) {
				notification.setStatus(NotificationStatus.SENT);
				notification.setProviderMessageId(result.providerMessageId());
				notification.setSentAt(Instant.now());
				notification.setLastErrorCode(null);
				notification.setAttemptCount(notification.getAttemptCount() + 1);
				notificationRepository.save(notification);
				log.info("notification.sent id={} type={}", notification.getId(), notification.getType());
				return;
			}
			handleFailure(notification, result.safeErrorCode(), result.retryable());
		} catch (EmailProviderException ex) {
			handleFailure(notification, ex.getCode(), ex.isRetryable());
		} catch (Exception ex) {
			log.warn("notification.failed id={} code=PROCESSING_FAILED", notificationId);
			handleFailure(notification, "PROCESSING_FAILED", true);
		}
	}

	private void handleFailure(Notification notification, String errorCode, boolean retryable) {
		int nextAttempt = notification.getAttemptCount() + 1;
		notification.setAttemptCount(nextAttempt);
		notification.setLastErrorCode(errorCode);
		notification.setProvider(emailProvider.providerName());

		if (!retryable || nextAttempt >= notification.getMaxAttempts()) {
			failPermanent(notification, errorCode);
			return;
		}

		int backoffIndex = Math.min(nextAttempt - 1, BACKOFF.length - 1);
		notification.setStatus(NotificationStatus.PENDING);
		notification.setNextAttemptAt(Instant.now().plus(BACKOFF[backoffIndex]));
		notificationRepository.save(notification);
		log.info("notification.retry id={} attempt={} nextAttemptAt={}",
				notification.getId(), nextAttempt, notification.getNextAttemptAt());
	}

	private void failPermanent(Notification notification, String errorCode) {
		notification.setStatus(NotificationStatus.FAILED);
		notification.setLastErrorCode(errorCode);
		notification.setNextAttemptAt(Instant.now().plus(Duration.ofDays(3650)));
		notificationRepository.save(notification);
		log.warn("notification.failed id={} code={}", notification.getId(), errorCode);
	}
}
