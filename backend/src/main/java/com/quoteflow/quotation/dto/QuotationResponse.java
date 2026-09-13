package com.quoteflow.quotation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.quoteflow.quotation.DiscountType;
import com.quoteflow.quotation.Quotation;
import com.quoteflow.quotation.QuotationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record QuotationResponse(
		UUID id,
		UUID customerId,
		String quotationNumber,
		QuotationStatus status,
		String currency,
		LocalDate issueDate,
		LocalDate validUntil,
		String customerDisplayName,
		String customerCompanyName,
		String customerEmail,
		String customerPhone,
		String customerAddressLine1,
		String customerAddressLine2,
		String customerCity,
		String customerStateRegion,
		String customerPostalCode,
		String customerCountryCode,
		String customerTaxId,
		String businessName,
		String businessEmail,
		String businessPhone,
		String businessAddressLine1,
		String businessAddressLine2,
		String businessCity,
		String businessStateRegion,
		String businessPostalCode,
		String businessCountryCode,
		String businessTaxId,
		String notes,
		String terms,
		DiscountType discountType,
		BigDecimal discountValue,
		BigDecimal taxRate,
		BigDecimal subtotal,
		BigDecimal discountAmount,
		BigDecimal taxAmount,
		BigDecimal totalAmount,
		long version,
		List<QuotationItemResponse> items,
		UUID convertedInvoiceId,
		String convertedInvoiceNumber,
		Instant createdAt,
		Instant updatedAt
) {
	public static QuotationResponse from(Quotation quotation) {
		return from(quotation, null, null);
	}

	public static QuotationResponse from(Quotation quotation, UUID convertedInvoiceId, String convertedInvoiceNumber) {
		List<QuotationItemResponse> items = quotation.getItems().stream()
				.map(QuotationItemResponse::from)
				.toList();
		return new QuotationResponse(
				quotation.getId(),
				quotation.getCustomerId(),
				quotation.getQuotationNumber(),
				quotation.getStatus(),
				quotation.getCurrency(),
				quotation.getIssueDate(),
				quotation.getValidUntil(),
				quotation.getCustomerDisplayName(),
				quotation.getCustomerCompanyName(),
				quotation.getCustomerEmail(),
				quotation.getCustomerPhone(),
				quotation.getCustomerAddressLine1(),
				quotation.getCustomerAddressLine2(),
				quotation.getCustomerCity(),
				quotation.getCustomerStateRegion(),
				quotation.getCustomerPostalCode(),
				quotation.getCustomerCountryCode(),
				quotation.getCustomerTaxId(),
				quotation.getBusinessName(),
				quotation.getBusinessEmail(),
				quotation.getBusinessPhone(),
				quotation.getBusinessAddressLine1(),
				quotation.getBusinessAddressLine2(),
				quotation.getBusinessCity(),
				quotation.getBusinessStateRegion(),
				quotation.getBusinessPostalCode(),
				quotation.getBusinessCountryCode(),
				quotation.getBusinessTaxId(),
				quotation.getNotes(),
				quotation.getTerms(),
				quotation.getDiscountType(),
				quotation.getDiscountValue(),
				quotation.getTaxRate(),
				quotation.getSubtotal(),
				quotation.getDiscountAmount(),
				quotation.getTaxAmount(),
				quotation.getTotalAmount(),
				quotation.getVersion(),
				items,
				convertedInvoiceId,
				convertedInvoiceNumber,
				quotation.getCreatedAt(),
				quotation.getUpdatedAt());
	}
}
