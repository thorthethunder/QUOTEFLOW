package com.quoteflow.billing;

import com.quoteflow.subscription.PlanId;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Maps QuoteFlow plan + interval → configured provider plan IDs.
 * Never accepts provider plan IDs from the browser.
 */
@Component
public class ProviderPlanResolver {

	private final BillingProperties properties;

	public ProviderPlanResolver(BillingProperties properties) {
		this.properties = properties;
	}

	public String resolveProviderPlanId(PlanId plan, BillingInterval interval) {
		if (isFakeProvider()) {
			return "plan_fake_" + plan.name().toLowerCase(Locale.ROOT) + "_" + interval.name().toLowerCase(Locale.ROOT);
		}
		String id = switch (plan) {
			case PRO -> switch (interval) {
				case MONTHLY -> properties.getRazorpay().getProMonthlyPlanId();
				case ANNUAL -> properties.getRazorpay().getProAnnualPlanId();
			};
			case BUSINESS -> switch (interval) {
				case MONTHLY -> properties.getRazorpay().getBusinessMonthlyPlanId();
				case ANNUAL -> null;
			};
			case FREE -> null;
		};
		if (!StringUtils.hasText(id)) {
			return null;
		}
		return id.trim();
	}

	public boolean isCheckoutConfigured(PlanId plan, BillingInterval interval) {
		if (plan == PlanId.FREE) {
			return false;
		}
		if (plan == PlanId.BUSINESS && interval == BillingInterval.ANNUAL) {
			return false;
		}
		return StringUtils.hasText(resolveProviderPlanId(plan, interval));
	}

	public boolean isBillingOperationallyReady() {
		if (!properties.isEnabled()) {
			return false;
		}
		if (isFakeProvider()) {
			return true;
		}
		BillingProperties.Razorpay rz = properties.getRazorpay();
		return StringUtils.hasText(rz.getKeyId())
				&& StringUtils.hasText(rz.getKeySecret())
				&& StringUtils.hasText(rz.getWebhookSecret())
				&& (StringUtils.hasText(rz.getProMonthlyPlanId()) || StringUtils.hasText(rz.getProAnnualPlanId()));
	}

	public List<BillingInterval> availableIntervals(PlanId plan) {
		List<BillingInterval> intervals = new ArrayList<>();
		for (BillingInterval interval : BillingInterval.values()) {
			if (isCheckoutConfigured(plan, interval)) {
				intervals.add(interval);
			}
		}
		return List.copyOf(intervals);
	}

	public int totalCount(BillingInterval interval) {
		return interval == BillingInterval.ANNUAL
				? properties.getRazorpay().getAnnualTotalCount()
				: properties.getRazorpay().getMonthlyTotalCount();
	}

	public boolean isFakeProvider() {
		return "FAKE".equalsIgnoreCase(properties.getProvider());
	}

	public PlanId resolveQuoteFlowPlan(String providerPlanId) {
		if (!StringUtils.hasText(providerPlanId)) {
			return null;
		}
		String id = providerPlanId.trim();
		if (isFakeProvider()) {
			if (id.contains("business")) {
				return PlanId.BUSINESS;
			}
			if (id.contains("pro")) {
				return PlanId.PRO;
			}
			return null;
		}
		BillingProperties.Razorpay rz = properties.getRazorpay();
		if (id.equals(rz.getBusinessMonthlyPlanId())) {
			return PlanId.BUSINESS;
		}
		if (id.equals(rz.getProMonthlyPlanId()) || id.equals(rz.getProAnnualPlanId())) {
			return PlanId.PRO;
		}
		return null;
	}

	public String publicKeyId() {
		if (isFakeProvider()) {
			return "rzp_test_fake_key";
		}
		return properties.getRazorpay().getKeyId();
	}

	public String normalizeProviderName() {
		if (!properties.isEnabled()) {
			return "NONE";
		}
		return properties.getProvider() == null
				? "NONE"
				: properties.getProvider().trim().toUpperCase(Locale.ROOT);
	}
}
