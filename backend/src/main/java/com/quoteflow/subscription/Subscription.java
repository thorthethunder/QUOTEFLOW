package com.quoteflow.subscription;

import com.quoteflow.billing.BillingInterval;
import com.quoteflow.common.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * QuoteFlow platform subscription for a tenant Business.
 * Separate from tenant Invoice/Payment (customer collections).
 */
@Entity
@Table(name = "subscriptions")
public class Subscription extends BaseAuditableEntity {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(name = "business_id", nullable = false, unique = true, updatable = false)
	private UUID businessId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40)
	private PlanId plan;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SubscriptionStatus status;

	@Column(name = "current_period_start")
	private Instant currentPeriodStart;

	@Column(name = "current_period_end")
	private Instant currentPeriodEnd;

	@Column(length = 40)
	private String provider;

	@Column(name = "provider_customer_id", length = 200)
	private String providerCustomerId;

	@Column(name = "provider_subscription_id", length = 200)
	private String providerSubscriptionId;

	@Column(name = "provider_plan_id", length = 200)
	private String providerPlanId;

	@Column(name = "provider_status", length = 40)
	private String providerStatus;

	@Enumerated(EnumType.STRING)
	@Column(name = "billing_interval", length = 20)
	private BillingInterval billingInterval;

	@Column(name = "cancel_at_period_end", nullable = false)
	private boolean cancelAtPeriodEnd;

	@Column(name = "cancelled_at")
	private Instant cancelledAt;

	@Column(name = "last_provider_event_at")
	private Instant lastProviderEventAt;

	protected Subscription() {
	}

	public Subscription(UUID businessId, PlanId plan, SubscriptionStatus status) {
		this.id = UUID.randomUUID();
		this.businessId = businessId;
		this.plan = plan;
		this.status = status;
		this.cancelAtPeriodEnd = false;
	}

	public UUID getId() {
		return id;
	}

	public UUID getBusinessId() {
		return businessId;
	}

	public PlanId getPlan() {
		return plan;
	}

	public void setPlan(PlanId plan) {
		this.plan = plan;
	}

	public SubscriptionStatus getStatus() {
		return status;
	}

	public void setStatus(SubscriptionStatus status) {
		this.status = status;
	}

	public Instant getCurrentPeriodStart() {
		return currentPeriodStart;
	}

	public void setCurrentPeriodStart(Instant currentPeriodStart) {
		this.currentPeriodStart = currentPeriodStart;
	}

	public Instant getCurrentPeriodEnd() {
		return currentPeriodEnd;
	}

	public void setCurrentPeriodEnd(Instant currentPeriodEnd) {
		this.currentPeriodEnd = currentPeriodEnd;
	}

	public String getProvider() {
		return provider;
	}

	public void setProvider(String provider) {
		this.provider = provider;
	}

	public String getProviderCustomerId() {
		return providerCustomerId;
	}

	public void setProviderCustomerId(String providerCustomerId) {
		this.providerCustomerId = providerCustomerId;
	}

	public String getProviderSubscriptionId() {
		return providerSubscriptionId;
	}

	public void setProviderSubscriptionId(String providerSubscriptionId) {
		this.providerSubscriptionId = providerSubscriptionId;
	}

	public String getProviderPlanId() {
		return providerPlanId;
	}

	public void setProviderPlanId(String providerPlanId) {
		this.providerPlanId = providerPlanId;
	}

	public String getProviderStatus() {
		return providerStatus;
	}

	public void setProviderStatus(String providerStatus) {
		this.providerStatus = providerStatus;
	}

	public BillingInterval getBillingInterval() {
		return billingInterval;
	}

	public void setBillingInterval(BillingInterval billingInterval) {
		this.billingInterval = billingInterval;
	}

	public boolean isCancelAtPeriodEnd() {
		return cancelAtPeriodEnd;
	}

	public void setCancelAtPeriodEnd(boolean cancelAtPeriodEnd) {
		this.cancelAtPeriodEnd = cancelAtPeriodEnd;
	}

	public Instant getCancelledAt() {
		return cancelledAt;
	}

	public void setCancelledAt(Instant cancelledAt) {
		this.cancelledAt = cancelledAt;
	}

	public Instant getLastProviderEventAt() {
		return lastProviderEventAt;
	}

	public void setLastProviderEventAt(Instant lastProviderEventAt) {
		this.lastProviderEventAt = lastProviderEventAt;
	}
}
