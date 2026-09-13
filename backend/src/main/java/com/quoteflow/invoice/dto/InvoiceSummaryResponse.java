package com.quoteflow.invoice.dto;

import com.quoteflow.invoice.Invoice;
import com.quoteflow.invoice.InvoiceStatus;
import com.quoteflow.payment.InvoicePaymentState;
import com.quoteflow.payment.dto.PaymentSummaryResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InvoiceSummaryResponse(
		UUID id,
		String invoiceNumber,
		String customerDisplayName,
		String customerCompanyName,
		LocalDate issueDate,
		LocalDate dueDate,
		InvoiceStatus status,
		String currency,
		BigDecimal totalAmount,
		UUID sourceQuotationId,
		InvoicePaymentState paymentState,
		BigDecimal amountPaid,
		BigDecimal balanceDue,
		Instant updatedAt
) {
	public static InvoiceSummaryResponse from(Invoice invoice) {
		return from(invoice, PaymentSummaryResponse.unpaid(invoice.getTotalAmount()));
	}

	public static InvoiceSummaryResponse from(Invoice invoice, PaymentSummaryResponse paymentSummary) {
		return new InvoiceSummaryResponse(
				invoice.getId(),
				invoice.getInvoiceNumber(),
				invoice.getCustomerDisplayName(),
				invoice.getCustomerCompanyName(),
				invoice.getIssueDate(),
				invoice.getDueDate(),
				invoice.getStatus(),
				invoice.getCurrency(),
				invoice.getTotalAmount(),
				invoice.getSourceQuotationId(),
				paymentSummary.paymentState(),
				paymentSummary.amountPaid(),
				paymentSummary.balanceDue(),
				invoice.getUpdatedAt());
	}
}
