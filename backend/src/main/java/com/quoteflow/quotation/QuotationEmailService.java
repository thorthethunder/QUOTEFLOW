package com.quoteflow.quotation;

import com.quoteflow.common.EmailNormalizer;
import com.quoteflow.common.api.DomainApiException;
import com.quoteflow.notification.Notification;
import com.quoteflow.notification.NotificationRateLimiter;
import com.quoteflow.notification.NotificationReferenceType;
import com.quoteflow.notification.NotificationResponse;
import com.quoteflow.notification.NotificationService;
import com.quoteflow.notification.NotificationType;
import com.quoteflow.notification.email.EmailAddressValidator;
import com.quoteflow.notification.email.EmailProperties;
import com.quoteflow.notification.email.delivery.NotificationDeliveryService;
import com.quoteflow.notification.email.template.EmailTemplateRenderer;
import com.quoteflow.security.AuthenticatedUser;
import com.quoteflow.subscription.EntitlementService;
import com.quoteflow.subscription.FeatureKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Queues quotation emails via the notification outbox. Provider failure does not roll back SENT.
 */
@Service
public class QuotationEmailService {

	private static final Logger log = LoggerFactory.getLogger(QuotationEmailService.class);

	private final QuotationService quotationService;
	private final QuotationRepository quotationRepository;
	private final EntitlementService entitlementService;
	private final NotificationService notificationService;
	private final NotificationDeliveryService deliveryService;
	private final NotificationRateLimiter rateLimiter;
	private final EmailProperties emailProperties;

	public QuotationEmailService(
			QuotationService quotationService,
			QuotationRepository quotationRepository,
			EntitlementService entitlementService,
			NotificationService notificationService,
			NotificationDeliveryService deliveryService,
			NotificationRateLimiter rateLimiter,
			EmailProperties emailProperties) {
		this.quotationService = quotationService;
		this.quotationRepository = quotationRepository;
		this.entitlementService = entitlementService;
		this.notificationService = notificationService;
		this.deliveryService = deliveryService;
		this.rateLimiter = rateLimiter;
		this.emailProperties = emailProperties;
	}

	@Transactional
	public NotificationResponse sendEmail(
			AuthenticatedUser principal,
			UUID quotationId,
			SendQuotationEmailRequest request) {
		entitlementService.requireFeature(principal.getBusinessId(), FeatureKey.EMAIL_SENDING);

		if (!rateLimiter.tryAcquireTenant(principal.getBusinessId())
				|| !rateLimiter.tryAcquireDocument(principal.getBusinessId(), "QUOTATION", quotationId)) {
			throw new DomainApiException(
					HttpStatus.TOO_MANY_REQUESTS,
					"RATE_LIMITED",
					"Too many email requests. Try again shortly.");
		}

		boolean explicitResend = request != null && Boolean.TRUE.equals(request.resend());
		Long version = request != null ? request.version() : null;
		String customMessage = sanitizeMessage(request != null ? request.message() : null);

		Quotation quotation = quotationRepository.findDetailByIdAndBusinessId(quotationId, principal.getBusinessId())
				.orElseThrow(() -> new DomainApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Quotation not found"));

		if (quotation.getStatus() == QuotationStatus.CANCELLED) {
			throw new DomainApiException(HttpStatus.CONFLICT, "INVALID_STATUS", "Cancelled quotations cannot be emailed");
		}

		if (quotation.getStatus() == QuotationStatus.DRAFT) {
			quotationService.markSent(principal, quotationId, version);
			quotation = quotationRepository.findDetailByIdAndBusinessId(quotationId, principal.getBusinessId())
					.orElseThrow(() -> new DomainApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Quotation not found"));
		}

		String recipient = EmailAddressValidator.requireValid(
				EmailNormalizer.normalize(quotation.getCustomerEmail()));
		if (recipient == null) {
			throw new DomainApiException(
					HttpStatus.BAD_REQUEST,
					"RECIPIENT_EMAIL_MISSING",
					"Add a valid email address to this customer before sending");
		}

		String replyTo = EmailAddressValidator.requireValid(
				EmailNormalizer.normalize(quotation.getBusinessEmail()));

		Map<String, String> vars = new LinkedHashMap<>();
		vars.put("businessName", nullToEmpty(quotation.getBusinessName()));
		vars.put("customerName", nullToEmpty(quotation.getCustomerDisplayName()));
		vars.put("documentNumber", quotation.getQuotationNumber());
		vars.put("totalDisplay", formatMoney(quotation.getTotalAmount(), quotation.getCurrency()));
		vars.put("issueDate", quotation.getIssueDate() == null
				? ""
				: quotation.getIssueDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
		vars.put("customMessage", customMessage);
		vars.put("currency", quotation.getCurrency());

		String subject = EmailTemplateRenderer.sanitizeSubject(
				"Quotation " + quotation.getQuotationNumber() + " from " + nullToEmpty(quotation.getBusinessName()));

		String idempotencyKey = explicitResend
				? null
				: "quotation-email:" + quotationId + ":" + quotation.getVersion();

		Notification notification = notificationService.enqueue(new NotificationService.EnqueueCommand(
				principal.getBusinessId(),
				NotificationType.QUOTATION_EMAIL,
				recipient,
				quotation.getCustomerDisplayName(),
				subject,
				"quotation_email",
				vars,
				NotificationReferenceType.QUOTATION,
				quotationId,
				replyTo,
				true,
				idempotencyKey,
				explicitResend));

		log.info("quotation.email.requested quotationId={} notificationId={}", quotationId, notification.getId());
		return NotificationResponse.from(notification);
	}

	public void dispatchNow(UUID notificationId) {
		deliveryService.deliverClaimedOrPending(notificationId);
	}

	private String sanitizeMessage(String message) {
		if (message == null) {
			return "";
		}
		String trimmed = message.replace("\r", "").trim();
		if (trimmed.length() > emailProperties.getMaxCustomMessageLength()) {
			throw new DomainApiException(
					HttpStatus.BAD_REQUEST,
					"VALIDATION_ERROR",
					"Message must be at most " + emailProperties.getMaxCustomMessageLength() + " characters");
		}
		return trimmed;
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

	private static String formatMoney(BigDecimal amount, String currencyCode) {
		if (amount == null) {
			return "";
		}
		try {
			NumberFormat format = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
			format.setCurrency(Currency.getInstance(currencyCode == null ? "INR" : currencyCode));
			return format.format(amount);
		} catch (Exception ex) {
			return amount.toPlainString() + " " + (currencyCode == null ? "" : currencyCode);
		}
	}
}
