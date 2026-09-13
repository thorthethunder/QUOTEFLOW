package com.quoteflow.reporting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One collections series bucket (daily or monthly), single currency. */
public record CollectionsSeriesPoint(
		LocalDate periodStart,
		String granularity,
		String currency,
		BigDecimal amount
) {
}
