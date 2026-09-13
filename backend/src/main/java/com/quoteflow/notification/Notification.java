package com.quoteflow.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(name = "business_id", nullable = false, updatable = false)
	private UUID businessId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 60)
	private NotificationType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private NotificationChannel channel;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private NotificationStatus status;

	@Column(name = "recipient_email", nullable = false, length = 320)
	private String recipientEmail;

	@Column(name = "recipient_display_name", length = 200)
	private String recipientDisplayName;

	@Column(nullable = false, length = 300)
	private String subject;

	@Column(name = "template_key", nullable = false, length = 80)
	private String templateKey;

	@Column(name = "template_vars_json", nullable = false, columnDefinition = "TEXT")
	private String templateVarsJson;

	@Enumerated(EnumType.STRING)
	@Column(name = "reference_type", nullable = false, length = 40)
	private NotificationReferenceType referenceType;

	@Column(name = "reference_id", nullable = false)
	private UUID referenceId;

	@Column(name = "attempt_count", nullable = false)
	private int attemptCount;

	@Column(name = "max_attempts", nullable = false)
	private int maxAttempts;

	@Column(name = "next_attempt_at", nullable = false)
	private Instant nextAttemptAt;

	@Column(name = "last_error_code", length = 80)
	private String lastErrorCode;

	@Column(length = 40)
	private String provider;

	@Column(name = "provider_message_id", length = 200)
	private String providerMessageId;

	@Column(name = "idempotency_key", length = 120)
	private String idempotencyKey;

	@Column(name = "reply_to_email", length = 320)
	private String replyToEmail;

	@Column(name = "include_pdf_attachment", nullable = false)
	private boolean includePdfAttachment;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "sent_at")
	private Instant sentAt;

	protected Notification() {
	}

	public Notification(
			UUID businessId,
			NotificationType type,
			String recipientEmail,
			String recipientDisplayName,
			String subject,
			String templateKey,
			String templateVarsJson,
			NotificationReferenceType referenceType,
			UUID referenceId,
			String replyToEmail,
			boolean includePdfAttachment,
			String idempotencyKey,
			int maxAttempts) {
		this.id = UUID.randomUUID();
		this.businessId = businessId;
		this.type = type;
		this.channel = NotificationChannel.EMAIL;
		this.status = NotificationStatus.PENDING;
		this.recipientEmail = recipientEmail;
		this.recipientDisplayName = recipientDisplayName;
		this.subject = subject;
		this.templateKey = templateKey;
		this.templateVarsJson = templateVarsJson;
		this.referenceType = referenceType;
		this.referenceId = referenceId;
		this.replyToEmail = replyToEmail;
		this.includePdfAttachment = includePdfAttachment;
		this.idempotencyKey = idempotencyKey;
		this.attemptCount = 0;
		this.maxAttempts = maxAttempts;
		Instant now = Instant.now();
		this.nextAttemptAt = now;
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		if (createdAt == null) {
			createdAt = now;
		}
		updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getBusinessId() {
		return businessId;
	}

	public NotificationType getType() {
		return type;
	}

	public NotificationChannel getChannel() {
		return channel;
	}

	public NotificationStatus getStatus() {
		return status;
	}

	public void setStatus(NotificationStatus status) {
		this.status = status;
	}

	public String getRecipientEmail() {
		return recipientEmail;
	}

	public String getRecipientDisplayName() {
		return recipientDisplayName;
	}

	public String getSubject() {
		return subject;
	}

	public String getTemplateKey() {
		return templateKey;
	}

	public String getTemplateVarsJson() {
		return templateVarsJson;
	}

	public NotificationReferenceType getReferenceType() {
		return referenceType;
	}

	public UUID getReferenceId() {
		return referenceId;
	}

	public int getAttemptCount() {
		return attemptCount;
	}

	public void setAttemptCount(int attemptCount) {
		this.attemptCount = attemptCount;
	}

	public int getMaxAttempts() {
		return maxAttempts;
	}

	public Instant getNextAttemptAt() {
		return nextAttemptAt;
	}

	public void setNextAttemptAt(Instant nextAttemptAt) {
		this.nextAttemptAt = nextAttemptAt;
	}

	public String getLastErrorCode() {
		return lastErrorCode;
	}

	public void setLastErrorCode(String lastErrorCode) {
		this.lastErrorCode = lastErrorCode;
	}

	public String getProvider() {
		return provider;
	}

	public void setProvider(String provider) {
		this.provider = provider;
	}

	public String getProviderMessageId() {
		return providerMessageId;
	}

	public void setProviderMessageId(String providerMessageId) {
		this.providerMessageId = providerMessageId;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public String getReplyToEmail() {
		return replyToEmail;
	}

	public boolean isIncludePdfAttachment() {
		return includePdfAttachment;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

	public Instant getSentAt() {
		return sentAt;
	}

	public void setSentAt(Instant sentAt) {
		this.sentAt = sentAt;
	}

	/**
	 * Retry path: re-queue a FAILED notification for another delivery cycle.
	 * Does not create a second row — preserves audit of prior attempts via attempt_count reset note in logs.
	 */
	public void requeueForRetry() {
		this.status = NotificationStatus.PENDING;
		this.attemptCount = 0;
		this.lastErrorCode = null;
		this.providerMessageId = null;
		this.sentAt = null;
		Instant now = Instant.now();
		this.nextAttemptAt = now;
		this.updatedAt = now;
	}
}
