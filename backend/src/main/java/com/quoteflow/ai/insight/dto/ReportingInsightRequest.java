package com.quoteflow.ai.insight.dto;

import com.quoteflow.ai.insight.ReportingInsightPeriod;

public record ReportingInsightRequest(
		String question,
		ReportingInsightPeriod period,
		String comparison
) {
}
