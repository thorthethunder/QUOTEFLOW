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
@Table(name = "ai_workflow_steps")
public class AiWorkflowStep extends BaseAuditableEntity {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(name = "workflow_id", nullable = false, updatable = false)
	private UUID workflowId;

	@Column(name = "step_number", nullable = false, updatable = false)
	private int stepNumber;

	@Enumerated(EnumType.STRING)
	@Column(name = "step_type", nullable = false, length = 80, updatable = false)
	private AiWorkflowStepType stepType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40, updatable = false)
	private AiWorkflowStepClassification classification;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40)
	private AiWorkflowStepStatus status;

	@Column(name = "action_proposal_id")
	private UUID actionProposalId;

	@Column(name = "input_json", nullable = false, columnDefinition = "TEXT", updatable = false)
	private String inputJson;

	@Column(name = "output_json", columnDefinition = "TEXT")
	private String outputJson;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Column(name = "failure_code", length = 80)
	private String failureCode;

	@Version
	@Column(nullable = false)
	private long version;

	protected AiWorkflowStep() {
	}

	public AiWorkflowStep(UUID workflowId, int stepNumber, AiWorkflowStepType stepType,
			AiWorkflowStepClassification classification, String inputJson) {
		this.id = UUID.randomUUID();
		this.workflowId = workflowId;
		this.stepNumber = stepNumber;
		this.stepType = stepType;
		this.classification = classification;
		this.status = AiWorkflowStepStatus.PENDING;
		this.inputJson = inputJson;
	}

	public UUID getId() {
		return id;
	}

	public UUID getWorkflowId() {
		return workflowId;
	}

	public int getStepNumber() {
		return stepNumber;
	}

	public AiWorkflowStepType getStepType() {
		return stepType;
	}

	public AiWorkflowStepClassification getClassification() {
		return classification;
	}

	public AiWorkflowStepStatus getStatus() {
		return status;
	}

	void setStatus(AiWorkflowStepStatus status) {
		this.status = status;
	}

	public UUID getActionProposalId() {
		return actionProposalId;
	}

	void setActionProposalId(UUID actionProposalId) {
		this.actionProposalId = actionProposalId;
	}

	public String getInputJson() {
		return inputJson;
	}

	public String getOutputJson() {
		return outputJson;
	}

	void setOutputJson(String outputJson) {
		this.outputJson = outputJson;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	void setStartedAt(Instant startedAt) {
		this.startedAt = startedAt;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

	void setCompletedAt(Instant completedAt) {
		this.completedAt = completedAt;
	}

	public String getFailureCode() {
		return failureCode;
	}

	void setFailureCode(String failureCode) {
		this.failureCode = failureCode;
	}

	public long getVersion() {
		return version;
	}
}
