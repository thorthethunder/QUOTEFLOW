package com.quoteflow.ai.insight.dto;

import com.quoteflow.ai.insight.ReportingInsightType;
import com.quoteflow.reporting.dto.DashboardSummaryResponse;

import java.util.List;

public record ReportingInsightResponse(
		String answer,
		ReportingInsightType insightType,
		InsightPeriodDto period,
		InsightPeriodDto comparisonPeriod,
		DashboardSummaryResponse metrics,
		DashboardSummaryResponse comparisonMetrics,
		List<ReportingInsightFact> facts,
		List<InsightOutstandingInvoiceDto> topOutstandingInvoices,
		List<InsightCustomerOutstandingDto> customerOutstanding,
		List<ReportingInsightReference> references,
		List<String> warnings,
		boolean aiNarrativeAvailable
) {
}
