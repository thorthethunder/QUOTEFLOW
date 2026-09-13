package com.quoteflow.quotation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuotationCalculatorTest {

	@Test
	void calculatesCanonicalFinancialExample() {
		var result = QuotationCalculator.calculate(
				List.of(
						new QuotationCalculator.LineInput(0, "A", new BigDecimal("2"), new BigDecimal("100.00")),
						new QuotationCalculator.LineInput(1, "B", new BigDecimal("1"), new BigDecimal("50.00"))),
				DiscountType.PERCENTAGE,
				new BigDecimal("10"),
				new BigDecimal("18"));

		assertThat(result.subtotal()).isEqualByComparingTo("250.00");
		assertThat(result.discountAmount()).isEqualByComparingTo("25.00");
		assertThat(result.taxAmount()).isEqualByComparingTo("40.50");
		assertThat(result.totalAmount()).isEqualByComparingTo("265.50");
	}

	@Test
	void roundsHalfUpAtBoundary() {
		var halfUp = QuotationCalculator.calculate(
				List.of(new QuotationCalculator.LineInput(0, "A", new BigDecimal("1"), new BigDecimal("1.005"))),
				DiscountType.NONE,
				BigDecimal.ZERO,
				BigDecimal.ZERO);
		assertThat(halfUp.lines().getFirst().lineSubtotal()).isEqualByComparingTo("1.01");
		assertThat(halfUp.totalAmount()).isEqualByComparingTo("1.01");

		var roundDown = QuotationCalculator.calculate(
				List.of(new QuotationCalculator.LineInput(0, "A", new BigDecimal("1"), new BigDecimal("0.0049"))),
				DiscountType.NONE,
				BigDecimal.ZERO,
				BigDecimal.ZERO);
		assertThat(roundDown.totalAmount()).isEqualByComparingTo("0.00");

		var roundUp = QuotationCalculator.calculate(
				List.of(new QuotationCalculator.LineInput(0, "A", new BigDecimal("1"), new BigDecimal("0.005"))),
				DiscountType.NONE,
				BigDecimal.ZERO,
				BigDecimal.ZERO);
		assertThat(roundUp.totalAmount()).isEqualByComparingTo("0.01");
	}

	@Test
	void fixedDiscountCannotExceedSubtotal() {
		var result = QuotationCalculator.calculate(
				List.of(new QuotationCalculator.LineInput(0, "A", new BigDecimal("1"), new BigDecimal("10.00"))),
				DiscountType.FIXED,
				new BigDecimal("50.00"),
				new BigDecimal("10"));
		assertThat(result.discountAmount()).isEqualByComparingTo("10.00");
		assertThat(result.taxAmount()).isEqualByComparingTo("0.00");
		assertThat(result.totalAmount()).isEqualByComparingTo("0.00");
	}

	@Test
	void allowsZeroUnitPriceAndFractionalQuantity() {
		var result = QuotationCalculator.calculate(
				List.of(new QuotationCalculator.LineInput(0, "Complimentary", new BigDecimal("1.5"), BigDecimal.ZERO)),
				DiscountType.NONE,
				BigDecimal.ZERO,
				new BigDecimal("18"));
		assertThat(result.subtotal()).isEqualByComparingTo("0.00");
		assertThat(result.totalAmount()).isEqualByComparingTo("0.00");
	}

	@Test
	void rejectsNegativeQuantityAndOversizeItemList() {
		assertThatThrownBy(() -> QuotationCalculator.calculate(
				List.of(new QuotationCalculator.LineInput(0, "A", new BigDecimal("-1"), new BigDecimal("1"))),
				DiscountType.NONE,
				BigDecimal.ZERO,
				BigDecimal.ZERO))
				.isInstanceOf(IllegalArgumentException.class);

		assertThatThrownBy(() -> QuotationCalculator.calculate(
				List.of(),
				DiscountType.NONE,
				BigDecimal.ZERO,
				BigDecimal.ZERO))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
