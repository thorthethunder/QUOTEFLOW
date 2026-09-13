package com.quoteflow.payment.dto;

import com.quoteflow.payment.InvoicePaymentState;
import com.quoteflow.payment.PaymentSummaryCalculator;

import java.math.BigDecimal;

public record PaymentSummaryResponse(
		BigDecimal amountPaid,
		BigDecimal balanceDue,
		InvoicePaymentState paymentState
) {
	public static PaymentSummaryResponse from(PaymentSummaryCalculator.PaymentSummary summary) {
		return new PaymentSummaryResponse(summary.amountPaid(), summary.balanceDue(), summary.paymentState());
	}

	public static PaymentSummaryResponse unpaid(BigDecimal invoiceTotal) {
		return from(PaymentSummaryCalculator.summarize(invoiceTotal, java.util.List.of()));
	}
}
