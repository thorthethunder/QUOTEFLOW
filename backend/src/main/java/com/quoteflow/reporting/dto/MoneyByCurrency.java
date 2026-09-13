package com.quoteflow.reporting.dto;

import java.math.BigDecimal;

/**
 * Monetary amount for a single currency. Never sum across currencies in the UI.
 */
public record MoneyByCurrency(String currency, BigDecimal amount) {
}
