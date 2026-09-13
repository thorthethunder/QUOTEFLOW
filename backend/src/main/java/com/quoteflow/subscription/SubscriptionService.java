package com.quoteflow.subscription;

import com.quoteflow.billing.BillingInterval;
import com.quoteflow.billing.ProviderPlanResolver;
import com.quoteflow.common.api.DomainApiException;
import com.quoteflow.subscription.dto.EntitlementLimitsDto;
import com.quoteflow.subscription.dto.EntitlementPeriodDto;
import com.quoteflow.subscription.dto.EntitlementResponse;
import com.quoteflow.subscription.dto.FeatureFlagsDto;
import com.quoteflow.subscription.dto.PlanCatalogItemDto;
import com.quoteflow.subscription.dto.UsageMeterDto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class SubscriptionService {

	private final SubscriptionRepository subscriptionRepository;
	private final UsageService usageService;
	private final ProviderPlanResolver planResolver;

	public SubscriptionService(
			SubscriptionRepository subscriptionRepository,
			UsageService usageService,
			ProviderPlanResolver planResolver) {
		this.subscriptionRepository = subscriptionRepository;
		this.usageService = usageService;
		this.planResolver = planResolver;
	}

	@Transactional
	public Subscription ensureDefaultFreeSubscription(UUID businessId) {
		return subscriptionRepository.findByBusinessId(businessId).orElseGet(() ->
				subscriptionRepository.save(new Subscription(businessId, PlanId.FREE, SubscriptionStatus.ACTIVE)));
	}

	@Transactional(readOnly = true)
	public EntitlementResponse getEntitlements(UUID businessId) {
		Subscription subscription = subscriptionRepository.findByBusinessId(businessId)
				.orElseThrow(() -> new DomainApiException(
						HttpStatus.FORBIDDEN, "SUBSCRIPTION_NOT_FOUND", "Subscription not found for business"));
		PlanDefinition plan = PlanCatalog.require(subscription.getPlan());
		UsagePeriod period = usageService.currentCalendarMonth(businessId);
		boolean checkoutAvailable = planResolver.isBillingOperationallyReady();

		return new EntitlementResponse(
				plan.id(),
				plan.displayName(),
				subscription.getStatus(),
				new EntitlementPeriodDto(period.from(), period.to(), period.timezone()),
				new EntitlementLimitsDto(
						UsageMeterDto.of(usageService.countActiveCustomers(businessId), plan.activeCustomerLimit()),
						UsageMeterDto.of(
								usageService.countQuotationsInPeriod(businessId, period),
								plan.quotationsPerMonth()),
						UsageMeterDto.of(
								usageService.countInvoicesInPeriod(businessId, period),
								plan.invoicesPerMonth())),
				new FeatureFlagsDto(
						plan.removeQuoteFlowBranding(),
						plan.multiUser(),
						plan.advancedReports(),
						plan.emailSending(),
						plan.aiAssistant()),
				checkoutAvailable,
				subscription.getBillingInterval(),
				subscription.getCurrentPeriodStart(),
				subscription.getCurrentPeriodEnd(),
				subscription.isCancelAtPeriodEnd(),
				subscription.getProvider() == null ? "NONE" : subscription.getProvider(),
				subscription.getProviderStatus(),
				planResolver.availableIntervals(PlanId.PRO));
	}

	@Transactional(readOnly = true)
	public List<PlanCatalogItemDto> catalog() {
		boolean ready = planResolver.isBillingOperationallyReady();
		return PlanCatalog.all().stream()
				.map(def -> {
					List<BillingInterval> intervals = planResolver.availableIntervals(def.id());
					boolean available = ready && !intervals.isEmpty() && def.id() != PlanId.FREE;
					return PlanCatalogItemDto.from(def, available, intervals);
				})
				.toList();
	}

	/**
	 * Test/internal only — never expose as a production HTTP endpoint.
	 */
	@Transactional
	public Subscription forcePlanForTests(UUID businessId, PlanId planId) {
		Subscription subscription = subscriptionRepository.findByBusinessIdForUpdate(businessId)
				.orElseGet(() -> subscriptionRepository.save(
						new Subscription(businessId, PlanId.FREE, SubscriptionStatus.ACTIVE)));
		subscription.setPlan(planId);
		subscription.setStatus(SubscriptionStatus.ACTIVE);
		return subscriptionRepository.save(subscription);
	}
}
