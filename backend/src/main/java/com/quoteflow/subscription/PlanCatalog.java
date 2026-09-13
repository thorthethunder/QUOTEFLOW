package com.quoteflow.subscription;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Central catalog of QuoteFlow SaaS plans. Pricing is informational until Phase 12.
 */
public final class PlanCatalog {

	private static final Map<PlanId, PlanDefinition> PLANS = new EnumMap<>(PlanId.class);

	static {
		PLANS.put(PlanId.FREE, new PlanDefinition(
				PlanId.FREE,
				"Free",
				5,
				5,
				5,
				false,
				false,
				1,
				false,
				false,
				false,
				"₹0",
				"₹0"));
		PLANS.put(PlanId.PRO, new PlanDefinition(
				PlanId.PRO,
				"Pro",
				null,
				null,
				null,
				true,
				false,
				1,
				false,
				true,
				false,
				"₹199/month",
				"₹1,999/year"));
		PLANS.put(PlanId.BUSINESS, new PlanDefinition(
				PlanId.BUSINESS,
				"Business",
				null,
				null,
				null,
				true,
				true,
				null,
				false,
				true,
				false,
				"₹499/month",
				null));
	}

	private PlanCatalog() {
	}

	public static PlanDefinition require(PlanId planId) {
		PlanDefinition def = PLANS.get(planId);
		if (def == null) {
			throw new IllegalArgumentException("Unknown plan: " + planId);
		}
		return def;
	}

	public static List<PlanDefinition> all() {
		return List.of(require(PlanId.FREE), require(PlanId.PRO), require(PlanId.BUSINESS));
	}
}
