package com.quoteflow.billing.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quoteflow.billing.BillingProperties;
import com.quoteflow.billing.BillingTransaction;
import com.quoteflow.billing.BillingTransactionRepository;
import com.quoteflow.billing.ProviderPlanResolver;
import com.quoteflow.billing.provider.BillingProvider;
import com.quoteflow.billing.provider.BillingProviderException;
import com.quoteflow.billing.provider.ProviderSubscription;
import com.quoteflow.billing.provider.razorpay.RazorpaySignatureVerifier;
import com.quoteflow.billing.reconciliation.SubscriptionStateMapper;
import com.quoteflow.subscription.PlanId;
import com.quoteflow.subscription.Subscription;
import com.quoteflow.subscription.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
public class BillingWebhookService {

	private static final Logger log = LoggerFactory.getLogger(BillingWebhookService.class);

	private static final Set<String> HANDLED_EVENTS = Set.of(
			"subscription.authenticated",
			"subscription.activated",
			"subscription.charged",
			"subscription.pending",
			"subscription.halted",
			"subscription.cancelled",
			"subscription.completed",
			"subscription.paused",
			"subscription.resumed");

	private final BillingProperties billingProperties;
	private final ProviderPlanResolver planResolver;
	private final RazorpaySignatureVerifier signatureVerifier;
	private final BillingWebhookEventRepository webhookEventRepository;
	private final SubscriptionRepository subscriptionRepository;
	private final BillingTransactionRepository billingTransactionRepository;
	private final SubscriptionStateMapper stateMapper;
	private final BillingProvider billingProvider;
	private final ObjectMapper objectMapper;

	public BillingWebhookService(
			BillingProperties billingProperties,
			ProviderPlanResolver planResolver,
			RazorpaySignatureVerifier signatureVerifier,
			BillingWebhookEventRepository webhookEventRepository,
			SubscriptionRepository subscriptionRepository,
			BillingTransactionRepository billingTransactionRepository,
			SubscriptionStateMapper stateMapper,
			BillingProvider billingProvider,
			ObjectMapper objectMapper) {
		this.billingProperties = billingProperties;
		this.planResolver = planResolver;
		this.signatureVerifier = signatureVerifier;
		this.webhookEventRepository = webhookEventRepository;
		this.subscriptionRepository = subscriptionRepository;
		this.billingTransactionRepository = billingTransactionRepository;
		this.stateMapper = stateMapper;
		this.billingProvider = billingProvider;
		this.objectMapper = objectMapper;
	}

	@Transactional
	public void handleRazorpayWebhook(String rawBody, String signature, String eventIdHeader) {
		String webhookSecret = planResolver.isFakeProvider()
				? "fake_webhook_secret"
				: billingProperties.getRazorpay().getWebhookSecret();

		if (signature == null || signature.isBlank()) {
			log.warn("billing.webhook.rejected reason=missing_signature");
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid signature");
		}
		if (webhookSecret == null || webhookSecret.isBlank()) {
			log.warn("billing.webhook.rejected reason=webhook_secret_not_configured");
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid signature");
		}
		if (!signatureVerifier.verifyWebhookSignature(rawBody, signature, webhookSecret)) {
			log.warn("billing.webhook.rejected reason=invalid_signature");
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid signature");
		}

		JsonNode root;
		try {
			root = objectMapper.readTree(rawBody);
		} catch (Exception ex) {
			log.warn("billing.webhook.rejected reason=malformed_json");
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payload");
		}

		String eventType = root.path("event").asText("");
		String providerEventId = (eventIdHeader != null && !eventIdHeader.isBlank())
				? eventIdHeader
				: deterministicEventId(root, rawBody);

		if (webhookEventRepository.existsByProviderAndProviderEventId("RAZORPAY", providerEventId)) {
			log.info("billing.webhook.duplicate eventId={}", providerEventId);
			return;
		}

		JsonNode payloadEntity = root.path("payload").path("subscription").path("entity");
		if (payloadEntity.isMissingNode() || payloadEntity.isNull()) {
			payloadEntity = root.path("payload").path("subscription");
		}
		String providerSubscriptionId = text(payloadEntity, "id");
		String payloadHash = signatureVerifier.sha256Hex(rawBody);

		BillingWebhookEvent event = new BillingWebhookEvent(
				"RAZORPAY",
				providerEventId,
				eventType,
				payloadHash,
				providerSubscriptionId);
		try {
			webhookEventRepository.saveAndFlush(event);
		} catch (DataIntegrityViolationException ex) {
			log.info("billing.webhook.duplicate eventId={}", providerEventId);
			return;
		}

		log.info("billing.webhook.received eventType={} eventId={}", eventType, providerEventId);

		if (!HANDLED_EVENTS.contains(eventType)) {
			event.markIgnored("UNSUPPORTED_EVENT");
			return;
		}

		if (providerSubscriptionId == null) {
			event.markIgnored("UNKNOWN_SUBSCRIPTION");
			return;
		}

		Optional<Subscription> optional = subscriptionRepository.findByProviderSubscriptionId(providerSubscriptionId);
		if (optional.isEmpty()) {
			event.markIgnored("UNKNOWN_SUBSCRIPTION");
			log.warn("billing.webhook.unknown_subscription providerSubscriptionIdPresent=true");
			return;
		}

		Subscription subscription = subscriptionRepository.findByBusinessIdForUpdate(optional.get().getBusinessId())
				.orElse(optional.get());

		try {
			ProviderSubscription fromPayload = mapEntity(payloadEntity);
			ProviderSubscription providerSub = fromPayload;
			if ("subscription.charged".equals(eventType)
					|| "subscription.activated".equals(eventType)
					|| "subscription.authenticated".equals(eventType)) {
				try {
					ProviderSubscription fetched = billingProvider.fetchSubscription(providerSubscriptionId);
					providerSub = mergeProviderSnapshots(fromPayload, fetched);
				} catch (BillingProviderException ex) {
					log.warn("billing.webhook.fetch_failed eventType={}", eventType);
				}
			}

			PlanId targetPlan = planResolver.resolveQuoteFlowPlan(subscription.getProviderPlanId());
			if (targetPlan == null) {
				targetPlan = planResolver.resolveQuoteFlowPlan(providerSub.providerPlanId());
			}
			if (targetPlan == null && subscription.getPlan() != PlanId.FREE) {
				targetPlan = subscription.getPlan();
			}
			if (targetPlan == null) {
				targetPlan = PlanId.PRO;
			}

			Instant eventTime = epoch(payloadEntity, "created_at");
			boolean force = "subscription.activated".equals(eventType)
					|| "subscription.authenticated".equals(eventType)
					|| "subscription.cancelled".equals(eventType)
					|| "subscription.completed".equals(eventType);
			stateMapper.applyProviderSnapshot(subscription, providerSub, targetPlan, eventTime, force);
			subscriptionRepository.save(subscription);

			if ("subscription.charged".equals(eventType)) {
				recordCharge(root, subscription, targetPlan);
			}

			event.markProcessed();
			log.info("billing.subscription.activated_or_synced businessId={} plan={} providerStatus={}",
					subscription.getBusinessId(), subscription.getPlan(), subscription.getProviderStatus());
		} catch (Exception ex) {
			event.markFailed("PROCESSING_FAILED");
			log.warn("billing.webhook.processing_failed eventType={}", eventType);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Processing failed");
		}
	}

	private ProviderSubscription mergeProviderSnapshots(ProviderSubscription payload, ProviderSubscription fetched) {
		String status = stateMapper.providerStatusRank(fetched.status()) >= stateMapper.providerStatusRank(payload.status())
				? fetched.status()
				: payload.status();
		return new ProviderSubscription(
				payload.providerSubscriptionId() != null ? payload.providerSubscriptionId() : fetched.providerSubscriptionId(),
				fetched.providerCustomerId() != null ? fetched.providerCustomerId() : payload.providerCustomerId(),
				fetched.providerPlanId() != null ? fetched.providerPlanId() : payload.providerPlanId(),
				status,
				fetched.currentPeriodStart() != null ? fetched.currentPeriodStart() : payload.currentPeriodStart(),
				fetched.currentPeriodEnd() != null ? fetched.currentPeriodEnd() : payload.currentPeriodEnd(),
				fetched.cancelAtCycleEnd() || payload.cancelAtCycleEnd(),
				fetched.endedAt() != null ? fetched.endedAt() : payload.endedAt(),
				fetched.providerUpdatedAt() != null ? fetched.providerUpdatedAt() : payload.providerUpdatedAt(),
				fetched.notes() != null && !fetched.notes().isEmpty() ? fetched.notes() : payload.notes());
	}

	private void recordCharge(JsonNode root, Subscription subscription, PlanId plan) {
		JsonNode payment = root.path("payload").path("payment").path("entity");
		String paymentId = text(payment, "id");
		if (paymentId == null) {
			return;
		}
		if (billingTransactionRepository.findByProviderAndProviderPaymentId("RAZORPAY", paymentId).isPresent()) {
			return;
		}
		Long amount = payment.path("amount").isNumber() ? payment.path("amount").asLong() : null;
		String currency = text(payment, "currency");
		Instant occurred = epoch(payment, "created_at");
		if (occurred == null) {
			occurred = Instant.now();
		}
		billingTransactionRepository.save(new BillingTransaction(
				subscription.getBusinessId(),
				"RAZORPAY",
				paymentId,
				subscription.getProviderSubscriptionId(),
				plan,
				subscription.getBillingInterval(),
				amount,
				currency,
				occurred));
	}

	private ProviderSubscription mapEntity(JsonNode entity) {
		return new ProviderSubscription(
				text(entity, "id"),
				text(entity, "customer_id"),
				text(entity, "plan_id"),
				text(entity, "status") == null ? null : text(entity, "status").toLowerCase(Locale.ROOT),
				epoch(entity, "current_start"),
				epoch(entity, "current_end"),
				entity.path("cancel_at_cycle_end").asBoolean(false),
				epoch(entity, "ended_at"),
				epoch(entity, "created_at"),
				java.util.Map.of());
	}

	private String deterministicEventId(JsonNode root, String rawBody) {
		String event = root.path("event").asText("unknown");
		String subId = root.path("payload").path("subscription").path("entity").path("id").asText("");
		String paymentId = root.path("payload").path("payment").path("entity").path("id").asText("");
		String created = root.path("payload").path("subscription").path("entity").path("created_at").asText("");
		String seed = event + "|" + subId + "|" + paymentId + "|" + created + "|" + signatureVerifier.sha256Hex(rawBody);
		return "derived_" + signatureVerifier.sha256Hex(seed).substring(0, 32);
	}

	private static String text(JsonNode node, String field) {
		if (node == null || node.isMissingNode()) {
			return null;
		}
		JsonNode value = node.get(field);
		if (value == null || value.isNull()) {
			return null;
		}
		String text = value.asText();
		return text == null || text.isBlank() ? null : text;
	}

	private static Instant epoch(JsonNode node, String field) {
		if (node == null || node.isMissingNode()) {
			return null;
		}
		JsonNode value = node.get(field);
		if (value == null || value.isNull() || !value.canConvertToLong()) {
			return null;
		}
		long epoch = value.asLong();
		return epoch <= 0 ? null : Instant.ofEpochSecond(epoch);
	}
}
