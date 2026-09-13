package com.quoteflow.subscription;

import com.quoteflow.common.api.DomainApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-authoritative SaaS entitlements. Angular gates are UX only.
 */
@Service
public class EntitlementService {

	private final SubscriptionRepository subscriptionRepository;
	private final UsageService usageService;

	public EntitlementService(SubscriptionRepository subscriptionRepository, UsageService usageService) {
		this.subscriptionRepository = subscriptionRepository;
		this.usageService = usageService;
	}

	/**
	 * Locks the tenant subscription row, then enforces the given quota feature.
	 * Must be called inside the same transaction as the resource create.
	 */
	@Transactional
	public Subscription lockAndRequireQuota(UUID businessId, FeatureKey feature) {
		Subscription subscription = subscriptionRepository.findByBusinessIdForUpdate(businessId)
				.orElseThrow(() -> new DomainApiException(
						HttpStatus.FORBIDDEN, "SUBSCRIPTION_NOT_FOUND", "Subscription not found for business"));
		PlanDefinition plan = PlanCatalog.require(subscription.getPlan());
		UsagePeriod period = usageService.currentCalendarMonth(businessId);

		switch (feature) {
			case CUSTOMERS -> {
				Integer limit = plan.activeCustomerLimit();
				if (limit != null) {
					long used = usageService.countActiveCustomers(businessId);
					if (used >= limit) {
						throw limitReached(feature, used, limit, plan.id());
					}
				}
			}
			case QUOTATIONS_MONTHLY -> {
				Integer limit = plan.quotationsPerMonth();
				if (limit != null) {
					long used = usageService.countQuotationsInPeriod(businessId, period);
					if (used >= limit) {
						throw limitReached(feature, used, limit, plan.id());
					}
				}
			}
			case INVOICES_MONTHLY -> {
				Integer limit = plan.invoicesPerMonth();
				if (limit != null) {
					long used = usageService.countInvoicesInPeriod(businessId, period);
					if (used >= limit) {
						throw limitReached(feature, used, limit, plan.id());
					}
				}
			}
			default -> throw new DomainApiException(
					HttpStatus.FORBIDDEN,
					"FEATURE_NOT_AVAILABLE",
					"Feature is not a quota entitlement: " + feature,
					Map.of("feature", feature.name(), "plan", plan.id().name()));
		}
		return subscription;
	}

	@Transactional(readOnly = true)
	public void requireFeature(UUID businessId, FeatureKey feature) {
		PlanDefinition plan = effectivePlanDefinition(businessId);
		boolean allowed = switch (feature) {
			case EMAIL_SENDING -> plan.emailSending();
			case REMOVE_QUOTEFLOW_BRANDING -> plan.removeQuoteFlowBranding();
			case MULTI_USER -> plan.multiUser();
			case ADVANCED_REPORTS -> plan.advancedReports();
			case AI_ASSISTANT -> plan.aiAssistant();
			case CUSTOMERS, QUOTATIONS_MONTHLY, INVOICES_MONTHLY ->
					throw new DomainApiException(
							HttpStatus.BAD_REQUEST,
							"VALIDATION_ERROR",
							"Use lockAndRequireQuota for quota features");
		};
		if (!allowed) {
			throw new DomainApiException(
					HttpStatus.FORBIDDEN,
					"FEATURE_NOT_AVAILABLE",
					"Feature is not available on the current plan",
					Map.of("feature", feature.name(), "plan", plan.id().name()));
		}
	}

	@Transactional(readOnly = true)
	public boolean showQuoteFlowBranding(UUID businessId) {
		PlanId plan = effectivePlan(businessId);
		return !PlanCatalog.require(plan).removeQuoteFlowBranding();
	}

	@Transactional(readOnly = true)
	public PlanId effectivePlan(UUID businessId) {
		return subscriptionRepository.findByBusinessId(businessId)
				.map(Subscription::getPlan)
				.orElse(PlanId.FREE);
	}

	@Transactional(readOnly = true)
	public PlanDefinition effectivePlanDefinition(UUID businessId) {
		return PlanCatalog.require(effectivePlan(businessId));
	}

	private static DomainApiException limitReached(FeatureKey feature, long used, int limit, PlanId plan) {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("feature", feature.name());
		details.put("used", used);
		details.put("limit", limit);
		details.put("plan", plan.name());
		return new DomainApiException(
				HttpStatus.FORBIDDEN,
				"PLAN_LIMIT_REACHED",
				"Plan limit reached for " + feature.name(),
				details);
	}
}
