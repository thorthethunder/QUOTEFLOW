package com.quoteflow.quotation.pdf;

import com.quoteflow.quotation.DiscountType;
import com.quoteflow.quotation.QuotationStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Immutable PDF document model. Built from persisted quotation snapshots and totals — never from live Business/Customer.
 */
public record QuotationPdfDocument(
		String quotationNumber,
		QuotationStatus status,
		LocalDate issueDate,
		LocalDate validUntil,
		String currency,
		BusinessParty business,
		CustomerParty customer,
		List<LineItem> lines,
		DiscountType discountType,
		BigDecimal discountValue,
		BigDecimal taxRate,
		BigDecimal subtotal,
		BigDecimal discountAmount,
		BigDecimal taxAmount,
		BigDecimal totalAmount,
		String notes,
		String terms,
		boolean showQuoteFlowBranding
) {
	public record BusinessParty(
			String name,
			String email,
			String phone,
			String addressLine1,
			String addressLine2,
			String city,
			String stateRegion,
			String postalCode,
			String countryCode,
			String taxId
	) {
	}

	public record CustomerParty(
			String displayName,
			String companyName,
			String email,
			String phone,
			String addressLine1,
			String addressLine2,
			String city,
			String stateRegion,
			String postalCode,
			String countryCode,
			String taxId
	) {
	}

	public record LineItem(
			int position,
			String description,
			BigDecimal quantity,
			BigDecimal unitPrice,
			BigDecimal lineSubtotal
	) {
	}
}
