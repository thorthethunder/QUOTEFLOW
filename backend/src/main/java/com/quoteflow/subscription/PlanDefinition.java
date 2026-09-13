package com.quoteflow.subscription;

/**
 * Code-defined plan capabilities. {@code null} quota = unlimited (commercial).
 * Technical abuse caps (line items, page size) remain separate.
 */
public record PlanDefinition(
		PlanId id,
		String displayName,
		Integer activeCustomerLimit,
		Integer quotationsPerMonth,
		Integer invoicesPerMonth,
		boolean removeQuoteFlowBranding,
		boolean multiUser,
		Integer seatLimit,
		boolean advancedReports,
		boolean emailSending,
		boolean aiAssistant,
		String monthlyPriceDisplay,
		String yearlyPriceDisplay
) {
	public boolean isUnlimitedCustomers() {
		return activeCustomerLimit == null;
	}

	public boolean isUnlimitedQuotations() {
		return quotationsPerMonth == null;
	}

	public boolean isUnlimitedInvoices() {
		return invoicesPerMonth == null;
	}
}
