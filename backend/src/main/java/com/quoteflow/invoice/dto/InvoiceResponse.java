package com.quoteflow.invoice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.quoteflow.finance.DiscountType;
import com.quoteflow.invoice.Invoice;
import com.quoteflow.invoice.InvoiceStatus;
import com.quoteflow.payment.dto.PaymentSummaryResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record InvoiceResponse(
		UUID id,
		UUID customerId,
		UUID sourceQuotationId,
		String sourceQuotationNumber,
		String invoiceNumber,
		InvoiceStatus status,
		String currency,
		LocalDate issueDate,
		LocalDate dueDate,
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
		List<InvoiceItemResponse> items,
		PaymentSummaryResponse paymentSummary,
		Instant createdAt,
		Instant updatedAt
) {
	public static InvoiceResponse from(Invoice invoice) {
		return from(invoice, PaymentSummaryResponse.unpaid(invoice.getTotalAmount()));
	}

	public static InvoiceResponse from(Invoice invoice, PaymentSummaryResponse paymentSummary) {
		List<InvoiceItemResponse> items = invoice.getItems().stream()
				.map(InvoiceItemResponse::from)
				.toList();
		String sourceNumber = invoice.getSourceQuotation() != null
				? invoice.getSourceQuotation().getQuotationNumber()
				: null;
		return new InvoiceResponse(
				invoice.getId(),
				invoice.getCustomerId(),
				invoice.getSourceQuotationId(),
				sourceNumber,
				invoice.getInvoiceNumber(),
				invoice.getStatus(),
				invoice.getCurrency(),
				invoice.getIssueDate(),
				invoice.getDueDate(),
				invoice.getCustomerDisplayName(),
				invoice.getCustomerCompanyName(),
				invoice.getCustomerEmail(),
				invoice.getCustomerPhone(),
				invoice.getCustomerAddressLine1(),
				invoice.getCustomerAddressLine2(),
				invoice.getCustomerCity(),
				invoice.getCustomerStateRegion(),
				invoice.getCustomerPostalCode(),
				invoice.getCustomerCountryCode(),
				invoice.getCustomerTaxId(),
				invoice.getBusinessName(),
				invoice.getBusinessEmail(),
				invoice.getBusinessPhone(),
				invoice.getBusinessAddressLine1(),
				invoice.getBusinessAddressLine2(),
				invoice.getBusinessCity(),
				invoice.getBusinessStateRegion(),
				invoice.getBusinessPostalCode(),
				invoice.getBusinessCountryCode(),
				invoice.getBusinessTaxId(),
				invoice.getNotes(),
				invoice.getTerms(),
				invoice.getDiscountType(),
				invoice.getDiscountValue(),
				invoice.getTaxRate(),
				invoice.getSubtotal(),
				invoice.getDiscountAmount(),
				invoice.getTaxAmount(),
				invoice.getTotalAmount(),
				invoice.getVersion(),
				items,
				paymentSummary,
				invoice.getCreatedAt(),
				invoice.getUpdatedAt());
	}
}
