package com.quoteflow.quotation;

import com.quoteflow.finance.FinancialDocumentCalculator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Quotation facade over {@link FinancialDocumentCalculator}. Behavior unchanged from Phase 6.
 */
public final class QuotationCalculator {

	public static final int MONEY_SCALE = FinancialDocumentCalculator.MONEY_SCALE;
	public static final RoundingMode ROUNDING = FinancialDocumentCalculator.ROUNDING;
	public static final int MAX_ITEMS = FinancialDocumentCalculator.MAX_ITEMS;

	private QuotationCalculator() {
	}

	public static CalculationResult calculate(
			List<LineInput> lines,
			DiscountType discountType,
			BigDecimal discountValue,
			BigDecimal taxRate) {
		var financeType = switch (discountType) {
			case NONE -> com.quoteflow.finance.DiscountType.NONE;
			case PERCENTAGE -> com.quoteflow.finance.DiscountType.PERCENTAGE;
			case FIXED -> com.quoteflow.finance.DiscountType.FIXED;
		};
		List<FinancialDocumentCalculator.LineInput> financeLines = lines.stream()
				.map(l -> new FinancialDocumentCalculator.LineInput(l.position(), l.description(), l.quantity(), l.unitPrice()))
				.toList();
		FinancialDocumentCalculator.CalculationResult result =
				FinancialDocumentCalculator.calculate(financeLines, financeType, discountValue, taxRate);
		List<LineResult> mapped = result.lines().stream()
				.map(l -> new LineResult(l.position(), l.description(), l.quantity(), l.unitPrice(), l.lineSubtotal()))
				.toList();
		return new CalculationResult(mapped, result.subtotal(), result.discountAmount(), result.taxAmount(), result.totalAmount());
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
