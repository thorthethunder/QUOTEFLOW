package com.quoteflow.billing.provider;

/**
 * Abstraction over SaaS billing providers (Razorpay first).
 */
public interface BillingProvider {

	String providerName();

	ProviderSubscription createSubscription(CreateProviderSubscriptionCommand command);

	ProviderSubscription fetchSubscription(String providerSubscriptionId);

	ProviderSubscription cancelSubscription(String providerSubscriptionId, boolean cancelAtCycleEnd);
}
