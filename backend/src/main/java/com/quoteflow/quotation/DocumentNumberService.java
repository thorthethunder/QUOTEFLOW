package com.quoteflow.quotation;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DocumentNumberService {

	private final DocumentSequenceRepository documentSequenceRepository;

	public DocumentNumberService(DocumentSequenceRepository documentSequenceRepository) {
		this.documentSequenceRepository = documentSequenceRepository;
	}

	@Transactional
	public AllocatedNumber allocateQuotationNumber(UUID businessId) {
		return allocate(businessId, DocumentType.QUOTATION, this::formatQuotationNumber);
	}

	@Transactional
	public AllocatedNumber allocateInvoiceNumber(UUID businessId) {
		return allocate(businessId, DocumentType.INVOICE, this::formatInvoiceNumber);
	}

	@Transactional
	public AllocatedNumber allocateReceiptNumber(UUID businessId) {
		return allocate(businessId, DocumentType.RECEIPT, this::formatReceiptNumber);
	}

	private AllocatedNumber allocate(UUID businessId, DocumentType type, NumberFormatter formatter) {
		DocumentSequence sequence = documentSequenceRepository
				.findForUpdate(businessId, type)
				.orElseGet(() -> createInitial(businessId, type));

		long value = sequence.allocateNext();
		documentSequenceRepository.save(sequence);
		return new AllocatedNumber(value, formatter.format(value));
	}

	private DocumentSequence createInitial(UUID businessId, DocumentType type) {
		try {
			documentSequenceRepository.saveAndFlush(new DocumentSequence(businessId, type));
		} catch (DataIntegrityViolationException ignored) {
			// Concurrent first allocation — fall through to locked read.
		}
		return documentSequenceRepository
				.findForUpdate(businessId, type)
				.orElseThrow(() -> new IllegalStateException("Document sequence unavailable for " + type));
	}

	public String formatQuotationNumber(long sequenceValue) {
		return "Q-%06d".formatted(sequenceValue);
	}

	public String formatInvoiceNumber(long sequenceValue) {
		return "INV-%06d".formatted(sequenceValue);
	}

	public String formatReceiptNumber(long sequenceValue) {
		return "RCP-%06d".formatted(sequenceValue);
	}

	@FunctionalInterface
	private interface NumberFormatter {
		String format(long sequenceValue);
	}

	public record AllocatedNumber(long sequenceValue, String documentNumber) {
	}
}
