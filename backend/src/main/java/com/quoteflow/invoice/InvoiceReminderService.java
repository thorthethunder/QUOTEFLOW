package com.quoteflow.invoice;

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
import com.quoteflow.payment.PaymentService;
import com.quoteflow.payment.dto.PaymentSummaryResponse;
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
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class InvoiceReminderService {

	private static final Logger log = LoggerFactory.getLogger(InvoiceReminderService.class);

	private final InvoiceRepository invoiceRepository;
	private final PaymentService paymentService;
	private final EntitlementService entitlementService;
	private final NotificationService notificationService;
	private final NotificationDeliveryService deliveryService;
	private final NotificationRateLimiter rateLimiter;
	private final EmailProperties emailProperties;

	public InvoiceReminderService(
			InvoiceRepository invoiceRepository,
			PaymentService paymentService,
			EntitlementService entitlementService,
			NotificationService notificationService,
			NotificationDeliveryService deliveryService,
			NotificationRateLimiter rateLimiter,
			EmailProperties emailProperties) {
		this.invoiceRepository = invoiceRepository;
		this.paymentService = paymentService;
		this.entitlementService = entitlementService;
		this.notificationService = notificationService;
		this.deliveryService = deliveryService;
		this.rateLimiter = rateLimiter;
		this.emailProperties = emailProperties;
	}

	@Transactional
	public NotificationResponse sendReminder(
			AuthenticatedUser principal,
			UUID invoiceId,
			SendInvoiceReminderRequest request) {
		entitlementService.requireFeature(principal.getBusinessId(), FeatureKey.EMAIL_SENDING);

		if (!rateLimiter.tryAcquireTenant(principal.getBusinessId())
				|| !rateLimiter.tryAcquireDocument(principal.getBusinessId(), "INVOICE", invoiceId)) {
			throw new DomainApiException(
					HttpStatus.TOO_MANY_REQUESTS,
					"RATE_LIMITED",
					"Too many email requests. Try again shortly.");
		}

		Invoice invoice = invoiceRepository.findDetailByIdAndBusinessId(invoiceId, principal.getBusinessId())
				.orElseThrow(() -> new DomainApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Invoice not found"));

		if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
			throw new DomainApiException(HttpStatus.CONFLICT, "INVALID_STATUS", "Cancelled invoices cannot receive reminders");
		}
		if (invoice.getStatus() != InvoiceStatus.SENT) {
			throw new DomainApiException(HttpStatus.CONFLICT, "INVALID_STATUS", "Only sent invoices can receive reminders");
		}

		PaymentSummaryResponse summary = paymentService.summaryForInvoice(invoice, principal.getBusinessId());
		if (summary.balanceDue() == null || summary.balanceDue().compareTo(BigDecimal.ZERO) <= 0) {
			throw new DomainApiException(
					HttpStatus.CONFLICT,
					"INVOICE_NOT_OUTSTANDING",
					"Invoice has no outstanding balance");
		}

		String recipient = EmailAddressValidator.requireValid(
				EmailNormalizer.normalize(invoice.getCustomerEmail()));
		if (recipient == null) {
			throw new DomainApiException(
					HttpStatus.BAD_REQUEST,
					"RECIPIENT_EMAIL_MISSING",
					"Add a valid email address to this customer before sending");
		}

		ReminderTone tone = request != null && request.tone() != null ? request.tone() : ReminderTone.STANDARD;
		boolean explicitResend = request != null && Boolean.TRUE.equals(request.resend());
		String customMessage = sanitizeMessage(request != null ? request.message() : null);

		String templateKey = switch (tone) {
			case FRIENDLY -> "invoice_reminder_friendly";
			case FIRM -> "invoice_reminder_firm";
			case STANDARD -> "invoice_reminder_standard";
		};

		Map<String, String> vars = new LinkedHashMap<>();
		vars.put("businessName", nullToEmpty(invoice.getBusinessName()));
		vars.put("customerName", nullToEmpty(invoice.getCustomerDisplayName()));
		vars.put("documentNumber", invoice.getInvoiceNumber());
		vars.put("balanceDisplay", formatMoney(summary.balanceDue(), invoice.getCurrency()));
		vars.put("customMessage", customMessage);
		vars.put("currency", invoice.getCurrency());

		String subject = EmailTemplateRenderer.sanitizeSubject(
				"Payment reminder: " + invoice.getInvoiceNumber() + " from " + nullToEmpty(invoice.getBusinessName()));

		String replyTo = EmailAddressValidator.requireValid(
				EmailNormalizer.normalize(invoice.getBusinessEmail()));

		String idempotencyKey = explicitResend
				? null
				: "invoice-reminder:" + invoiceId + ":" + summary.balanceDue().toPlainString();

		Notification notification = notificationService.enqueue(new NotificationService.EnqueueCommand(
				principal.getBusinessId(),
				NotificationType.INVOICE_REMINDER,
				recipient,
				invoice.getCustomerDisplayName(),
				subject,
				templateKey,
				vars,
				NotificationReferenceType.INVOICE,
				invoiceId,
				replyTo,
				false,
				idempotencyKey,
				explicitResend));

		log.info("reminder.email.requested invoiceId={} notificationId={}", invoiceId, notification.getId());
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
