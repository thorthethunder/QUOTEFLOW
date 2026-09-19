package com.quoteflow.ai.workflow;

import com.quoteflow.common.BaseAuditableEntity;
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
@Table(name = "ai_workflows")
public class AiWorkflow extends BaseAuditableEntity {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(name = "business_id", nullable = false, updatable = false)
	private UUID businessId;

	@Column(name = "requested_by_user_id", nullable = false, updatable = false)
	private UUID requestedByUserId;

	@Enumerated(EnumType.STRING)
	@Column(name = "workflow_type", nullable = false, length = 60, updatable = false)
	private AiWorkflowType workflowType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40)
	private AiWorkflowStatus status;

	@Column(nullable = false, length = 1000, updatable = false)
	private String goal;

	@Column(name = "idempotency_key", length = 120, updatable = false)
	private String idempotencyKey;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Version
	@Column(nullable = false)
	private long version;

	protected AiWorkflow() {
	}

	public AiWorkflow(UUID businessId, UUID requestedByUserId, AiWorkflowType workflowType,
			String goal, String idempotencyKey, Instant now, Instant expiresAt) {
		this.id = UUID.randomUUID();
		this.businessId = businessId;
		this.requestedByUserId = requestedByUserId;
		this.workflowType = workflowType;
		this.status = AiWorkflowStatus.CREATED;
		this.goal = goal;
		this.idempotencyKey = idempotencyKey;
		this.startedAt = now;
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

	public AiWorkflowType getWorkflowType() {
		return workflowType;
	}

	public AiWorkflowStatus getStatus() {
		return status;
	}

	void setStatus(AiWorkflowStatus status) {
		this.status = status;
	}

	public String getGoal() {
		return goal;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

	void setCompletedAt(Instant completedAt) {
		this.completedAt = completedAt;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public long getVersion() {
		return version;
	}

	public boolean isExpired(Instant now) {
		return !expiresAt.isAfter(now)
				&& status != AiWorkflowStatus.COMPLETED
				&& status != AiWorkflowStatus.COMPLETED_WITH_PARTIAL_RESULTS
				&& status != AiWorkflowStatus.CANCELLED
				&& status != AiWorkflowStatus.FAILED
				&& status != AiWorkflowStatus.EXPIRED;
	}
}
