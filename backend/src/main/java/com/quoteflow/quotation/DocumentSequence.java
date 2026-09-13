package com.quoteflow.quotation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "document_sequences")
@IdClass(DocumentSequence.DocumentSequenceId.class)
public class DocumentSequence {

	@Id
	@Column(name = "business_id", nullable = false)
	private UUID businessId;

	@Id
	@Enumerated(EnumType.STRING)
	@Column(name = "document_type", nullable = false, length = 40)
	private DocumentType documentType;

	@Column(name = "next_value", nullable = false)
	private long nextValue;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected DocumentSequence() {
	}

	public DocumentSequence(UUID businessId, DocumentType documentType) {
		this.businessId = businessId;
		this.documentType = documentType;
		this.nextValue = 1L;
		this.updatedAt = Instant.now();
	}

	public long allocateNext() {
		long allocated = this.nextValue;
		this.nextValue = allocated + 1;
		this.updatedAt = Instant.now();
		return allocated;
	}

	public UUID getBusinessId() {
		return businessId;
	}

	public DocumentType getDocumentType() {
		return documentType;
	}

	public long getNextValue() {
		return nextValue;
	}

	public static final class DocumentSequenceId implements Serializable {
		private UUID businessId;
		private DocumentType documentType;

		public DocumentSequenceId() {
		}

		public DocumentSequenceId(UUID businessId, DocumentType documentType) {
			this.businessId = businessId;
			this.documentType = documentType;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (!(o instanceof DocumentSequenceId that)) {
				return false;
			}
			return Objects.equals(businessId, that.businessId) && documentType == that.documentType;
		}

		@Override
		public int hashCode() {
			return Objects.hash(businessId, documentType);
		}
	}
}
