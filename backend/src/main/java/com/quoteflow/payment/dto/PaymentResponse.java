package com.quoteflow.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.quoteflow.payment.Payment;
import com.quoteflow.payment.PaymentMethod;
import com.quoteflow.payment.PaymentRecordStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentResponse(
		UUID id,
		UUID invoiceId,
		String receiptNumber,
		BigDecimal amount,
		String currency,
		LocalDate paymentDate,
		PaymentMethod paymentMethod,
		String reference,
		String notes,
		PaymentRecordStatus status,
		String invoiceNumberSnapshot,
		BigDecimal invoiceTotalAtPayment,
		BigDecimal previousPaidAmount,
		BigDecimal remainingBalanceAfterPayment,
		Instant voidedAt,
		String voidReason,
		Instant createdAt,
		Instant updatedAt,
		PaymentSummaryResponse invoicePaymentSummary
) {
	public static PaymentResponse from(Payment payment) {
		return from(payment, null);
	}

	public static PaymentResponse from(Payment payment, PaymentSummaryResponse summary) {
		return new PaymentResponse(
				payment.getId(),
				payment.getInvoiceId(),
				payment.getReceiptNumber(),
				payment.getAmount(),
				payment.getCurrency(),
				payment.getPaymentDate(),
				payment.getPaymentMethod(),
				payment.getReference(),
				payment.getNotes(),
				payment.getStatus(),
				payment.getInvoiceNumberSnapshot(),
				payment.getInvoiceTotalAtPayment(),
				payment.getPreviousPaidAmount(),
				payment.getRemainingBalanceAfterPayment(),
				payment.getVoidedAt(),
				payment.getVoidReason(),
				payment.getCreatedAt(),
				payment.getUpdatedAt(),
				summary);
	}
}
