package com.quoteflow.invoice;

public class QuotationAlreadyInvoicedException extends RuntimeException {

	private final java.util.UUID existingInvoiceId;

	public QuotationAlreadyInvoicedException(java.util.UUID existingInvoiceId) {
		super("Quotation has already been converted to an invoice.");
		this.existingInvoiceId = existingInvoiceId;
	}

	public java.util.UUID getExistingInvoiceId() {
		return existingInvoiceId;
	}
}
