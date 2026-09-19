package com.quoteflow.ai.usage;

import java.math.BigDecimal;

public record AiCostEstimate(
		BigDecimal amount,
		String currency,
		boolean known
) {
	public static AiCostEstimate unknown() {
		return new AiCostEstimate(null, null, false);
	}

	public static AiCostEstimate zero(String currency) {
		return new AiCostEstimate(BigDecimal.ZERO.setScale(8), currency, true);
	}
}
