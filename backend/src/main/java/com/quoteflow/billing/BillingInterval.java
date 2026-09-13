package com.quoteflow.billing;

/**
 * QuoteFlow billing cadence. Mapped server-side to configured Razorpay plan IDs.
 */
public enum BillingInterval {
	MONTHLY,
	ANNUAL
}
