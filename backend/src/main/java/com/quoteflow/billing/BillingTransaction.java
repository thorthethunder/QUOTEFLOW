package com.quoteflow.billing;

import com.quoteflow.subscription.PlanId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * QuoteFlow SaaS revenue events — never confused with tenant invoice payments.
 */
@Entity
@Table(name = "billing_transactions")
public class BillingTransaction {

	public enum Status {
		RECORDED,
		IGNORED
	}

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(name = "business_id", nullable = false)
	private UUID businessId;

	@Column(nullable = false, length = 40)
	private String provider;

	@Column(name = "provider_payment_id", length = 200)
	private String providerPaymentId;

	@Column(name = "provider_subscription_id", length = 200)
	private String providerSubscriptionId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40)
	private PlanId plan;

	@Enumerated(EnumType.STRING)
	@Column(name = "billing_interval", length = 20)
	private BillingInterval billingInterval;

	@Column(name = "amount_minor")
	private Long amountMinor;

	@Column(length = 3)
	private String currency;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40)
	private Status status;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected BillingTransaction() {
	}

	public BillingTransaction(
			UUID businessId,
			String provider,
			String providerPaymentId,
			String providerSubscriptionId,
			PlanId plan,
			BillingInterval billingInterval,
			Long amountMinor,
			String currency,
			Instant occurredAt) {
		this.id = UUID.randomUUID();
		this.businessId = businessId;
		this.provider = provider;
		this.providerPaymentId = providerPaymentId;
		this.providerSubscriptionId = providerSubscriptionId;
		this.plan = plan;
		this.billingInterval = billingInterval;
		this.amountMinor = amountMinor;
		this.currency = currency;
		this.status = Status.RECORDED;
		this.occurredAt = occurredAt;
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public String getProviderPaymentId() {
		return providerPaymentId;
	}
}
