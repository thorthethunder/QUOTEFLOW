package com.quoteflow.billing.checkout;

import com.quoteflow.billing.BillingInterval;
import com.quoteflow.billing.BillingProperties;
import com.quoteflow.billing.ProviderPlanResolver;
import com.quoteflow.billing.provider.BillingProvider;
import com.quoteflow.billing.provider.BillingProviderException;
import com.quoteflow.billing.provider.CreateProviderSubscriptionCommand;
import com.quoteflow.billing.provider.ProviderSubscription;
import com.quoteflow.billing.provider.fake.FakeBillingProvider;
import com.quoteflow.billing.provider.razorpay.RazorpaySignatureVerifier;
import com.quoteflow.billing.reconciliation.SubscriptionStateMapper;
import com.quoteflow.common.api.DomainApiException;
import com.quoteflow.identity.TenantRole;
import com.quoteflow.security.AuthenticatedUser;
import com.quoteflow.subscription.PlanId;
import com.quoteflow.subscription.Subscription;
import com.quoteflow.subscription.SubscriptionRepository;
import com.quoteflow.subscription.SubscriptionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class BillingCheckoutService {

	private static final Logger log = LoggerFactory.getLogger(BillingCheckoutService.class);
	private static final Set<String> REUSABLE_PROVIDER_STATUSES = Set.of("created");

	private final BillingProperties billingProperties;
	private final ProviderPlanResolver planResolver;
	private final BillingProvider billingProvider;
	private final SubscriptionRepository subscriptionRepository;
	private final SubscriptionStateMapper stateMapper;
	private final RazorpaySignatureVerifier signatureVerifier;
	private final ObjectProvider<FakeBillingProvider> fakeBillingProvider;

	public BillingCheckoutService(
			BillingProperties billingProperties,
			ProviderPlanResolver planResolver,
			BillingProvider billingProvider,
			SubscriptionRepository subscriptionRepository,
			SubscriptionStateMapper stateMapper,
			RazorpaySignatureVerifier signatureVerifier,
			ObjectProvider<FakeBillingProvider> fakeBillingProvider) {
		this.billingProperties = billingProperties;
		this.planResolver = planResolver;
		this.billingProvider = billingProvider;
		this.subscriptionRepository = subscriptionRepository;
		this.stateMapper = stateMapper;
		this.signatureVerifier = signatureVerifier;
		this.fakeBillingProvider = fakeBillingProvider;
	}

	@Transactional
	public CheckoutResponse createCheckout(AuthenticatedUser principal, CreateCheckoutRequest request) {
		requireOwner(principal);
		ensureBillingAvailable();

		PlanId plan = request.plan();
		BillingInterval interval = request.billingInterval();
		if (plan == PlanId.FREE) {
			throw new DomainApiException(HttpStatus.BAD_REQUEST, "INVALID_PLAN_TRANSITION", "Cannot checkout Free plan");
		}
		if (!planResolver.isCheckoutConfigured(plan, interval)) {
			throw new DomainApiException(
					HttpStatus.BAD_REQUEST,
					"BILLING_CONFIGURATION_ERROR",
					"Requested plan/interval is not configured for checkout");
		}

		UUID businessId = principal.getBusinessId();
		Subscription subscription = subscriptionRepository.findByBusinessIdForUpdate(businessId)
				.orElseThrow(() -> new DomainApiException(
						HttpStatus.FORBIDDEN, "SUBSCRIPTION_NOT_FOUND", "Subscription not found for business"));

		if (subscription.getPlan() == plan
				&& subscription.getStatus() == SubscriptionStatus.ACTIVE
				&& stateMapper.grantsPaidEntitlements(subscription.getProviderStatus())
				&& !subscription.isCancelAtPeriodEnd()) {
			throw new DomainApiException(
					HttpStatus.CONFLICT,
					"BILLING_ALREADY_ACTIVE",
					"An active paid subscription already exists for this plan");
		}

		if (subscription.getPlan() != PlanId.FREE
				&& stateMapper.grantsPaidEntitlements(subscription.getProviderStatus())
				&& subscription.getPlan() != plan) {
			throw new DomainApiException(
					HttpStatus.BAD_REQUEST,
					"INVALID_PLAN_TRANSITION",
					"Paid-to-paid plan changes are not supported in this release. Cancel first, then upgrade.");
		}

		String providerPlanId = planResolver.resolveProviderPlanId(plan, interval);

		if (subscription.getProviderSubscriptionId() != null
				&& REUSABLE_PROVIDER_STATUSES.contains(safeLower(subscription.getProviderStatus()))
				&& providerPlanId.equals(subscription.getProviderPlanId())
				&& interval == subscription.getBillingInterval()) {
			log.info("billing.checkout.reused businessId={} providerSubscriptionId={}",
					businessId, subscription.getProviderSubscriptionId());
			return new CheckoutResponse(
					billingProvider.providerName(),
					planResolver.publicKeyId(),
					subscription.getProviderSubscriptionId(),
					plan,
					interval);
		}

		Map<String, String> notes = new LinkedHashMap<>();
		notes.put("business_id", businessId.toString());
		notes.put("plan", plan.name());
		notes.put("billing_interval", interval.name());

		ProviderSubscription created;
		try {
			created = billingProvider.createSubscription(new CreateProviderSubscriptionCommand(
					providerPlanId,
					planResolver.totalCount(interval),
					notes));
		} catch (BillingProviderException ex) {
			log.warn("billing.checkout.failed code={}", ex.getCode());
			throw new DomainApiException(
					HttpStatus.BAD_GATEWAY,
					"BILLING_CHECKOUT_FAILED",
					"Unable to start checkout with billing provider");
		}

		subscription.setProvider(billingProvider.providerName());
		subscription.setProviderSubscriptionId(created.providerSubscriptionId());
		subscription.setProviderCustomerId(created.providerCustomerId());
		subscription.setProviderPlanId(created.providerPlanId());
		subscription.setProviderStatus(safeLower(created.status()));
		subscription.setBillingInterval(interval);
		subscription.setCancelAtPeriodEnd(false);
		subscription.setLastProviderEventAt(created.providerUpdatedAt());
		subscriptionRepository.save(subscription);

		log.info("billing.checkout.created businessId={} plan={} interval={}", businessId, plan, interval);
		return new CheckoutResponse(
				billingProvider.providerName(),
				planResolver.publicKeyId(),
				created.providerSubscriptionId(),
				plan,
				interval);
	}

	@Transactional
	public VerifyCheckoutResponse verifyCheckout(AuthenticatedUser principal, VerifyCheckoutRequest request) {
		requireOwner(principal);
		ensureBillingAvailable();

		UUID businessId = principal.getBusinessId();
		Subscription subscription = subscriptionRepository.findByBusinessIdForUpdate(businessId)
				.orElseThrow(() -> new DomainApiException(
						HttpStatus.FORBIDDEN, "SUBSCRIPTION_NOT_FOUND", "Subscription not found for business"));

		if (subscription.getProviderSubscriptionId() == null
				|| !subscription.getProviderSubscriptionId().equals(request.razorpaySubscriptionId())) {
			throw new DomainApiException(
					HttpStatus.FORBIDDEN,
					"BILLING_VERIFICATION_FAILED",
					"Subscription does not belong to this workspace");
		}

		String secret = planResolver.isFakeProvider()
				? "fake_key_secret"
				: billingProperties.getRazorpay().getKeySecret();

		boolean valid = signatureVerifier.verifyCheckoutSignature(
				request.razorpayPaymentId(),
				request.razorpaySubscriptionId(),
				request.razorpaySignature(),
				secret);
		if (!valid) {
			log.warn("billing.checkout.verify_rejected businessId={}", businessId);
			throw new DomainApiException(
					HttpStatus.BAD_REQUEST,
					"BILLING_VERIFICATION_FAILED",
					"Checkout signature verification failed");
		}

		ProviderSubscription providerSub;
		try {
			if (planResolver.isFakeProvider()) {
				FakeBillingProvider fake = fakeBillingProvider.getObject();
				providerSub = fake.simulateActivated(request.razorpaySubscriptionId());
			} else {
				providerSub = billingProvider.fetchSubscription(request.razorpaySubscriptionId());
			}
		} catch (BillingProviderException ex) {
			throw new DomainApiException(
					HttpStatus.BAD_GATEWAY,
					"BILLING_PROVIDER_UNAVAILABLE",
					"Unable to confirm subscription with billing provider");
		}

		PlanId targetPlan = planResolver.resolveQuoteFlowPlan(subscription.getProviderPlanId());
		if (targetPlan == null) {
			targetPlan = PlanId.PRO;
		}

		stateMapper.applyProviderSnapshot(subscription, providerSub, targetPlan, null, true);
		subscriptionRepository.save(subscription);

		boolean activated = stateMapper.grantsPaidEntitlements(subscription.getProviderStatus());
		log.info("billing.checkout.verified businessId={} activated={} providerStatus={}",
				businessId, activated, subscription.getProviderStatus());

		return new VerifyCheckoutResponse(
				subscription.getPlan(),
				subscription.getStatus(),
				subscription.getProviderStatus(),
				subscription.getBillingInterval(),
				activated);
	}

	@Transactional
	public VerifyCheckoutResponse cancelAtPeriodEnd(AuthenticatedUser principal) {
		requireOwner(principal);
		ensureBillingAvailable();

		Subscription subscription = subscriptionRepository.findByBusinessIdForUpdate(principal.getBusinessId())
				.orElseThrow(() -> new DomainApiException(
						HttpStatus.FORBIDDEN, "SUBSCRIPTION_NOT_FOUND", "Subscription not found for business"));

		if (subscription.getProviderSubscriptionId() == null
				|| !stateMapper.grantsPaidEntitlements(subscription.getProviderStatus())) {
			throw new DomainApiException(
					HttpStatus.BAD_REQUEST,
					"INVALID_PLAN_TRANSITION",
					"No cancellable paid subscription");
		}

		try {
			ProviderSubscription cancelled = billingProvider.cancelSubscription(
					subscription.getProviderSubscriptionId(), true);
			stateMapper.applyProviderSnapshot(
					subscription,
					cancelled,
					subscription.getPlan(),
					null,
					true);
			subscription.setCancelAtPeriodEnd(true);
			subscriptionRepository.save(subscription);
			log.info("billing.subscription.cancel_at_period_end businessId={}", principal.getBusinessId());
		} catch (BillingProviderException ex) {
			throw new DomainApiException(
					HttpStatus.BAD_GATEWAY,
					"BILLING_PROVIDER_UNAVAILABLE",
					"Unable to cancel subscription with billing provider");
		}

		return new VerifyCheckoutResponse(
				subscription.getPlan(),
				subscription.getStatus(),
				subscription.getProviderStatus(),
				subscription.getBillingInterval(),
				stateMapper.grantsPaidEntitlements(subscription.getProviderStatus()));
	}

	private void ensureBillingAvailable() {
		if (!billingProperties.isEnabled()) {
			throw new DomainApiException(
					HttpStatus.SERVICE_UNAVAILABLE,
					"BILLING_NOT_AVAILABLE",
					"Billing is not enabled");
		}
		if (!planResolver.isBillingOperationallyReady()) {
			throw new DomainApiException(
					HttpStatus.SERVICE_UNAVAILABLE,
					"BILLING_CONFIGURATION_ERROR",
					"Billing is not configured");
		}
	}

	private static void requireOwner(AuthenticatedUser principal) {
		if (principal.getTenantRole() != TenantRole.OWNER) {
			throw new DomainApiException(
					HttpStatus.FORBIDDEN,
					"FORBIDDEN",
					"Only the workspace owner can manage billing");
		}
	}

	private static String safeLower(String value) {
		return value == null ? null : value.toLowerCase(Locale.ROOT);
	}
}
