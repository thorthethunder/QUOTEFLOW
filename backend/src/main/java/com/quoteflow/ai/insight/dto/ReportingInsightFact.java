package com.quoteflow.ai.insight.dto;

import java.math.BigDecimal;

public record ReportingInsightFact(
		String metric,
		String currency,
		BigDecimal currentValue,
		BigDecimal previousValue,
		BigDecimal absoluteChange,
		BigDecimal percentageChange,
		String comparisonReason
) {
}
