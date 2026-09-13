package com.quoteflow.billing.reconciliation;

import com.quoteflow.billing.provider.ProviderSubscription;
import com.quoteflow.subscription.PlanId;
import com.quoteflow.subscription.Subscription;
import com.quoteflow.subscription.SubscriptionStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;

/**
 * Maps Razorpay provider statuses → internal QuoteFlow subscription plan/status.
 *
 * Entitlement grant (paid plan): authenticated | active | pending | halted | paused
 * Downgrade to FREE: cancelled | completed | expired
 * created: checkout pending — keep FREE entitlements
 */
@Component
public class SubscriptionStateMapper {

	private static final Set<String> PAID_PROVIDER_STATUSES = Set.of(
			"authenticated", "active", "pending", "halted", "paused", "resumed");

	private static final Set<String> TERMINAL_FREE_STATUSES = Set.of(
			"cancelled", "completed", "expired");

	public boolean grantsPaidEntitlements(String providerStatus) {
		if (providerStatus == null) {
			return false;
		}
		return PAID_PROVIDER_STATUSES.contains(providerStatus.toLowerCase(Locale.ROOT));
	}

	public boolean isTerminalFree(String providerStatus) {
		if (providerStatus == null) {
			return false;
		}
		return TERMINAL_FREE_STATUSES.contains(providerStatus.toLowerCase(Locale.ROOT));
	}

	public int providerStatusRank(String providerStatus) {
		if (providerStatus == null) {
			return 0;
		}
		return switch (providerStatus.toLowerCase(Locale.ROOT)) {
			case "created" -> 10;
			case "authenticated" -> 20;
			case "active" -> 30;
			case "pending" -> 25;
			case "halted" -> 22;
			case "paused" -> 24;
			case "resumed" -> 30;
			case "cancelled", "completed", "expired" -> 100;
			default -> 5;
		};
	}

	/**
	 * Apply provider snapshot to the single tenant subscription row.
	 * @return true if a meaningful mutation occurred
	 */
	public boolean applyProviderSnapshot(
			Subscription subscription,
			ProviderSubscription provider,
			PlanId requestedPaidPlan,
			Instant eventTime,
			boolean force) {
		String incomingStatus = provider.status() == null ? null : provider.status().toLowerCase(Locale.ROOT);
		Instant effectiveEvent = eventTime != null
				? eventTime
				: (provider.providerUpdatedAt() != null ? provider.providerUpdatedAt() : Instant.now());

		if (!force
				&& subscription.getLastProviderEventAt() != null
				&& effectiveEvent.isBefore(subscription.getLastProviderEventAt())
				&& !isTerminalFree(incomingStatus)) {
			// Stale non-terminal event — ignore
			return false;
		}

		if (!force
				&& subscription.getProviderStatus() != null
				&& incomingStatus != null
				&& providerStatusRank(incomingStatus) < providerStatusRank(subscription.getProviderStatus())
				&& !isTerminalFree(incomingStatus)) {
			return false;
		}

		subscription.setProvider(subscription.getProvider() == null ? "RAZORPAY" : subscription.getProvider());
		subscription.setProviderSubscriptionId(provider.providerSubscriptionId());
		if (provider.providerCustomerId() != null) {
			subscription.setProviderCustomerId(provider.providerCustomerId());
		}
		if (provider.providerPlanId() != null) {
			subscription.setProviderPlanId(provider.providerPlanId());
		}
		subscription.setProviderStatus(incomingStatus);
		if (provider.currentPeriodStart() != null) {
			subscription.setCurrentPeriodStart(provider.currentPeriodStart());
		}
		if (provider.currentPeriodEnd() != null) {
			subscription.setCurrentPeriodEnd(provider.currentPeriodEnd());
		}
		if (provider.cancelAtCycleEnd()) {
			subscription.setCancelAtPeriodEnd(true);
		}
		subscription.setLastProviderEventAt(effectiveEvent);

		if (grantsPaidEntitlements(incomingStatus)) {
			PlanId plan = requestedPaidPlan != null ? requestedPaidPlan : inferPaidPlan(subscription);
			subscription.setPlan(plan);
			if ("pending".equals(incomingStatus) || "halted".equals(incomingStatus)) {
				subscription.setStatus(SubscriptionStatus.PAST_DUE);
			} else {
				subscription.setStatus(SubscriptionStatus.ACTIVE);
			}
			if (provider.endedAt() == null) {
				subscription.setCancelledAt(null);
			}
			return true;
		}

		if (isTerminalFree(incomingStatus)) {
			subscription.setPlan(PlanId.FREE);
			subscription.setStatus(SubscriptionStatus.ACTIVE);
			subscription.setCancelAtPeriodEnd(false);
			subscription.setCancelledAt(provider.endedAt() != null ? provider.endedAt() : effectiveEvent);
			subscription.setBillingInterval(null);
			return true;
		}

		// created / unknown: keep FREE until activation authority
		if ("created".equals(incomingStatus) && subscription.getPlan() != PlanId.FREE
				&& !grantsPaidEntitlements(subscription.getProviderStatus())) {
			// no-op for plan
		}
		return true;
	}

	private PlanId inferPaidPlan(Subscription subscription) {
		if (subscription.getPlan() == PlanId.BUSINESS || subscription.getPlan() == PlanId.PRO) {
			return subscription.getPlan();
		}
		return PlanId.PRO;
	}
}
