package com.quoteflow.payment;

import com.quoteflow.finance.FinancialDocumentCalculator;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Authoritative aggregate of RECORDED payments against an invoice total.
 */
public final class PaymentSummaryCalculator {

	private PaymentSummaryCalculator() {
	}

	public static PaymentSummary summarize(BigDecimal invoiceTotal, List<BigDecimal> recordedAmounts) {
		Objects.requireNonNull(invoiceTotal, "invoiceTotal");
		Objects.requireNonNull(recordedAmounts, "recordedAmounts");
		BigDecimal total = invoiceTotal.setScale(FinancialDocumentCalculator.MONEY_SCALE, FinancialDocumentCalculator.ROUNDING);
		BigDecimal amountPaid = BigDecimal.ZERO.setScale(FinancialDocumentCalculator.MONEY_SCALE, FinancialDocumentCalculator.ROUNDING);
		for (BigDecimal amount : recordedAmounts) {
			if (amount == null || amount.signum() <= 0) {
				throw new IllegalArgumentException("payment amount must be > 0");
			}
			amountPaid = amountPaid.add(amount.setScale(
					FinancialDocumentCalculator.MONEY_SCALE, FinancialDocumentCalculator.ROUNDING));
		}
		if (amountPaid.compareTo(total) > 0) {
			throw new IllegalStateException("amountPaid exceeds invoice total");
		}
		BigDecimal balanceDue = total.subtract(amountPaid)
				.setScale(FinancialDocumentCalculator.MONEY_SCALE, FinancialDocumentCalculator.ROUNDING);
		InvoicePaymentState state;
		if (amountPaid.signum() == 0) {
			state = InvoicePaymentState.UNPAID;
		} else if (balanceDue.signum() == 0) {
			state = InvoicePaymentState.PAID;
		} else {
			state = InvoicePaymentState.PARTIALLY_PAID;
		}
		return new PaymentSummary(amountPaid, balanceDue, state);
	}

	public record PaymentSummary(BigDecimal amountPaid, BigDecimal balanceDue, InvoicePaymentState paymentState) {
	}
}
