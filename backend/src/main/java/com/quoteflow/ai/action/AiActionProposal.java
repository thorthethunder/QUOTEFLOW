package com.quoteflow.ai.action;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_action_proposals")
public class AiActionProposal {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(name = "business_id", nullable = false, updatable = false)
	private UUID businessId;

	@Column(name = "requested_by_user_id", nullable = false, updatable = false)
	private UUID requestedByUserId;

	@Enumerated(EnumType.STRING)
	@Column(name = "action_type", nullable = false, length = 60, updatable = false)
	private AiActionType actionType;

	@Column(name = "payload_json", nullable = false, columnDefinition = "TEXT", updatable = false)
	private String payloadJson;

	@Column(name = "payload_hash", nullable = false, length = 64, updatable = false)
	private String payloadHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AiActionProposalStatus status;

	@Column(nullable = false, length = 500, updatable = false)
	private String summary;

	@Column(name = "preview_json", columnDefinition = "TEXT", updatable = false)
	private String previewJson;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;

	@Column(name = "executed_at")
	private Instant executedAt;

	@Column(name = "cancelled_at")
	private Instant cancelledAt;

	@Column(name = "result_reference_type", length = 40)
	private String resultReferenceType;

	@Column(name = "result_reference_id")
	private UUID resultReferenceId;

	@Column(name = "failure_code", length = 80)
	private String failureCode;

	@Version
	@Column(nullable = false)
	private long version;

	protected AiActionProposal() {
	}

	public AiActionProposal(
			UUID businessId,
			UUID requestedByUserId,
			AiActionType actionType,
			String payloadJson,
			String payloadHash,
			String summary,
			String previewJson,
			Instant createdAt,
			Instant expiresAt) {
		this.id = UUID.randomUUID();
		this.businessId = businessId;
		this.requestedByUserId = requestedByUserId;
		this.actionType = actionType;
		this.payloadJson = payloadJson;
		this.payloadHash = payloadHash;
		this.status = AiActionProposalStatus.PENDING;
		this.summary = summary;
		this.previewJson = previewJson;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
	}

	public UUID getId() {
		return id;
	}

	public UUID getBusinessId() {
		return businessId;
	}

	public UUID getRequestedByUserId() {
		return requestedByUserId;
	}

	public AiActionType getActionType() {
		return actionType;
	}

	public String getPayloadJson() {
		return payloadJson;
	}

	public String getPayloadHash() {
		return payloadHash;
	}

	public AiActionProposalStatus getStatus() {
		return status;
	}

	public String getSummary() {
		return summary;
	}

	public String getPreviewJson() {
		return previewJson;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getExecutedAt() {
		return executedAt;
	}

	public Instant getCancelledAt() {
		return cancelledAt;
	}

	public String getResultReferenceType() {
		return resultReferenceType;
	}

	public UUID getResultReferenceId() {
		return resultReferenceId;
	}

	public String getFailureCode() {
		return failureCode;
	}

	public long getVersion() {
		return version;
	}

	public boolean isExpired(Instant now) {
		return status == AiActionProposalStatus.PENDING && !expiresAt.isAfter(now);
	}

	public void markExpired(Instant now) {
		if (status == AiActionProposalStatus.PENDING) {
			this.status = AiActionProposalStatus.EXPIRED;
		}
	}

	public void markCancelled(Instant now) {
		requirePending();
		this.status = AiActionProposalStatus.CANCELLED;
		this.cancelledAt = now;
	}

	public void markExecuted(Instant now, String resultType, UUID resultId) {
		requirePending();
		this.status = AiActionProposalStatus.EXECUTED;
		this.executedAt = now;
		this.resultReferenceType = resultType;
		this.resultReferenceId = resultId;
	}

	public void markFailed(String code) {
		if (status == AiActionProposalStatus.PENDING) {
			this.status = AiActionProposalStatus.FAILED;
			this.failureCode = code;
		}
	}

	private void requirePending() {
		if (status != AiActionProposalStatus.PENDING) {
			throw new IllegalStateException("Proposal is not PENDING: " + status);
		}
	}
}
