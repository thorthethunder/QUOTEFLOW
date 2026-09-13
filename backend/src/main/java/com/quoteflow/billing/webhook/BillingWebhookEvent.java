package com.quoteflow.billing.webhook;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "billing_webhook_events")
public class BillingWebhookEvent {

	public enum ProcessingStatus {
		RECEIVED,
		PROCESSED,
		IGNORED,
		FAILED
	}

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(nullable = false, length = 40)
	private String provider;

	@Column(name = "provider_event_id", nullable = false, length = 200)
	private String providerEventId;

	@Column(name = "event_type", nullable = false, length = 100)
	private String eventType;

	@Column(name = "payload_hash", nullable = false, length = 64)
	private String payloadHash;

	@Column(name = "received_at", nullable = false)
	private Instant receivedAt;

	@Column(name = "processed_at")
	private Instant processedAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "processing_status", nullable = false, length = 40)
	private ProcessingStatus processingStatus;

	@Column(name = "failure_reason_safe", length = 500)
	private String failureReasonSafe;

	@Column(name = "provider_subscription_id", length = 200)
	private String providerSubscriptionId;

	protected BillingWebhookEvent() {
	}

	public BillingWebhookEvent(
			String provider,
			String providerEventId,
			String eventType,
			String payloadHash,
			String providerSubscriptionId) {
		this.id = UUID.randomUUID();
		this.provider = provider;
		this.providerEventId = providerEventId;
		this.eventType = eventType;
		this.payloadHash = payloadHash;
		this.receivedAt = Instant.now();
		this.processingStatus = ProcessingStatus.RECEIVED;
		this.providerSubscriptionId = providerSubscriptionId;
	}

	public UUID getId() {
		return id;
	}

	public String getProvider() {
		return provider;
	}

	public String getProviderEventId() {
		return providerEventId;
	}

	public String getEventType() {
		return eventType;
	}

	public String getPayloadHash() {
		return payloadHash;
	}

	public Instant getReceivedAt() {
		return receivedAt;
	}

	public Instant getProcessedAt() {
		return processedAt;
	}

	public ProcessingStatus getProcessingStatus() {
		return processingStatus;
	}

	public String getFailureReasonSafe() {
		return failureReasonSafe;
	}

	public String getProviderSubscriptionId() {
		return providerSubscriptionId;
	}

	public void markProcessed() {
		this.processingStatus = ProcessingStatus.PROCESSED;
		this.processedAt = Instant.now();
		this.failureReasonSafe = null;
	}

	public void markIgnored(String reason) {
		this.processingStatus = ProcessingStatus.IGNORED;
		this.processedAt = Instant.now();
		this.failureReasonSafe = truncate(reason);
	}

	public void markFailed(String reason) {
		this.processingStatus = ProcessingStatus.FAILED;
		this.processedAt = Instant.now();
		this.failureReasonSafe = truncate(reason);
	}

	private static String truncate(String reason) {
		if (reason == null) {
			return null;
		}
		return reason.length() <= 500 ? reason : reason.substring(0, 500);
	}
}
