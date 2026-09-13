package com.quoteflow.finance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Shared authoritative money calculator for quotations and invoices.
 * Scale 2, RoundingMode.HALF_UP — see ADR-009.
 */
public final class FinancialDocumentCalculator {

	public static final int MONEY_SCALE = 2;
	public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
	public static final int MAX_ITEMS = 100;

	private FinancialDocumentCalculator() {
	}

	public static CalculationResult calculate(
			List<LineInput> lines,
			DiscountType discountType,
			BigDecimal discountValue,
			BigDecimal taxRate) {
		Objects.requireNonNull(lines, "lines");
		Objects.requireNonNull(discountType, "discountType");
		if (lines.isEmpty()) {
			throw new IllegalArgumentException("At least one line item is required");
		}
		if (lines.size() > MAX_ITEMS) {
			throw new IllegalArgumentException("Too many line items");
		}

		BigDecimal safeDiscountValue = zeroIfNull(discountValue);
		BigDecimal safeTaxRate = zeroIfNull(taxRate);
		if (safeDiscountValue.signum() < 0) {
			throw new IllegalArgumentException("discountValue must be >= 0");
		}
		if (safeTaxRate.signum() < 0 || safeTaxRate.compareTo(new BigDecimal("100")) > 0) {
			throw new IllegalArgumentException("taxRate must be between 0 and 100");
		}
		if (discountType == DiscountType.PERCENTAGE && safeDiscountValue.compareTo(new BigDecimal("100")) > 0) {
			throw new IllegalArgumentException("percentage discount must be <= 100");
		}

		List<LineResult> lineResults = new ArrayList<>(lines.size());
		BigDecimal subtotal = BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING);
		for (LineInput line : lines) {
			if (line.quantity() == null || line.quantity().signum() <= 0) {
				throw new IllegalArgumentException("quantity must be > 0");
			}
			if (line.unitPrice() == null || line.unitPrice().signum() < 0) {
				throw new IllegalArgumentException("unitPrice must be >= 0");
			}
			BigDecimal lineSubtotal = line.quantity()
					.multiply(line.unitPrice())
					.setScale(MONEY_SCALE, ROUNDING);
			lineResults.add(new LineResult(line.position(), line.description(), line.quantity(), line.unitPrice(), lineSubtotal));
			subtotal = subtotal.add(lineSubtotal);
		}

		BigDecimal discountAmount = switch (discountType) {
			case NONE -> BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING);
			case PERCENTAGE -> subtotal
					.multiply(safeDiscountValue)
					.divide(new BigDecimal("100"), MONEY_SCALE, ROUNDING);
			case FIXED -> safeDiscountValue.setScale(MONEY_SCALE, ROUNDING);
		};
		if (discountAmount.compareTo(subtotal) > 0) {
			discountAmount = subtotal;
		}

		BigDecimal taxable = subtotal.subtract(discountAmount).setScale(MONEY_SCALE, ROUNDING);
		BigDecimal taxAmount = taxable
				.multiply(safeTaxRate)
				.divide(new BigDecimal("100"), MONEY_SCALE, ROUNDING);
		BigDecimal total = taxable.add(taxAmount).setScale(MONEY_SCALE, ROUNDING);

		return new CalculationResult(
				List.copyOf(lineResults),
				subtotal,
				discountAmount,
				taxAmount,
				total);
	}

	private static BigDecimal zeroIfNull(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}

	public record LineInput(int position, String description, BigDecimal quantity, BigDecimal unitPrice) {
	}

	public record LineResult(
			int position,
			String description,
			BigDecimal quantity,
			BigDecimal unitPrice,
			BigDecimal lineSubtotal) {
	}

	public record CalculationResult(
			List<LineResult> lines,
			BigDecimal subtotal,
			BigDecimal discountAmount,
			BigDecimal taxAmount,
			BigDecimal totalAmount) {
	}
}
