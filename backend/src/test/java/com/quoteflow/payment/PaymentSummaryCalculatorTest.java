package com.quoteflow.payment;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentSummaryCalculatorTest {

	@Test
	void unpaidPartiallyPaidAndPaid() {
		var unpaid = PaymentSummaryCalculator.summarize(new BigDecimal("1000.00"), List.of());
		assertThat(unpaid.paymentState()).isEqualTo(InvoicePaymentState.UNPAID);
		assertThat(unpaid.amountPaid()).isEqualByComparingTo("0.00");
		assertThat(unpaid.balanceDue()).isEqualByComparingTo("1000.00");

		var partial = PaymentSummaryCalculator.summarize(
				new BigDecimal("1000.00"), List.of(new BigDecimal("300.00")));
		assertThat(partial.paymentState()).isEqualTo(InvoicePaymentState.PARTIALLY_PAID);
		assertThat(partial.amountPaid()).isEqualByComparingTo("300.00");
		assertThat(partial.balanceDue()).isEqualByComparingTo("700.00");

		var paid = PaymentSummaryCalculator.summarize(
				new BigDecimal("1000.00"),
				List.of(new BigDecimal("300.00"), new BigDecimal("700.00")));
		assertThat(paid.paymentState()).isEqualTo(InvoicePaymentState.PAID);
		assertThat(paid.balanceDue()).isEqualByComparingTo("0.00");
	}

	@Test
	void canonicalInvoicePartialThenFull() {
		var first = PaymentSummaryCalculator.summarize(
				new BigDecimal("265.50"), List.of(new BigDecimal("100.00")));
		assertThat(first.balanceDue()).isEqualByComparingTo("165.50");
		assertThat(first.paymentState()).isEqualTo(InvoicePaymentState.PARTIALLY_PAID);

		var full = PaymentSummaryCalculator.summarize(
				new BigDecimal("265.50"),
				List.of(new BigDecimal("100.00"), new BigDecimal("165.50")));
		assertThat(full.amountPaid()).isEqualByComparingTo("265.50");
		assertThat(full.paymentState()).isEqualTo(InvoicePaymentState.PAID);
	}

	@Test
	void rejectsOverTotal() {
		assertThatThrownBy(() -> PaymentSummaryCalculator.summarize(
				new BigDecimal("100.00"), List.of(new BigDecimal("101.00"))))
				.isInstanceOf(IllegalStateException.class);
	}
}
