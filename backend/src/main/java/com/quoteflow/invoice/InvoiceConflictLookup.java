package com.quoteflow.invoice;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Lookups that must succeed after a unique-constraint failure poisoned the caller transaction.
 */
@Service
public class InvoiceConflictLookup {

	private final InvoiceRepository invoiceRepository;

	public InvoiceConflictLookup(InvoiceRepository invoiceRepository) {
		this.invoiceRepository = invoiceRepository;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
	public Optional<UUID> findInvoiceIdBySourceQuotation(UUID quotationId, UUID businessId) {
		return invoiceRepository.findBySourceQuotation_IdAndBusiness_Id(quotationId, businessId)
				.map(Invoice::getId);
	}
}
