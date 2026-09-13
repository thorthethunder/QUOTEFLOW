package com.quoteflow.quotation.pdf;

import com.quoteflow.quotation.DiscountType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;

public final class DocumentMoneyFormatter {

	private DocumentMoneyFormatter() {
	}

	public static String formatAmount(BigDecimal amount, String currencyCode) {
		BigDecimal value = (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP);
		String code = currencyCode == null ? "USD" : currencyCode.trim().toUpperCase(Locale.ROOT);
		try {
			Currency currency = Currency.getInstance(code);
			Locale locale = switch (code) {
				case "INR" -> Locale.forLanguageTag("en-IN");
				case "EUR" -> Locale.GERMANY;
				case "GBP" -> Locale.UK;
				default -> Locale.US;
			};
			NumberFormat format = NumberFormat.getCurrencyInstance(locale);
			format.setCurrency(currency);
			int digits = currency.getDefaultFractionDigits();
			if (digits < 0) {
				digits = 2;
			}
			format.setMinimumFractionDigits(digits);
			format.setMaximumFractionDigits(digits);
			return format.format(value);
		} catch (IllegalArgumentException ex) {
			return code + " " + value.toPlainString();
		}
	}

	static String discountLabel(DiscountType type, BigDecimal discountValue) {
		if (type == DiscountType.PERCENTAGE) {
			return "Discount (" + stripTrailingZeros(discountValue) + "%)";
		}
		return "Discount";
	}

	static String taxLabel(BigDecimal taxRate) {
		return "Tax (" + stripTrailingZeros(taxRate) + "%)";
	}

	private static String stripTrailingZeros(BigDecimal value) {
		return value == null ? "0" : value.stripTrailingZeros().toPlainString();
	}
}
