package com.quoteflow.ai.insight.dto;

import java.util.UUID;

public record ReportingInsightReference(
		String type,
		UUID id,
		String displayNumber,
		String label
) {
	public static ReportingInsightReference invoice(UUID id, String number) {
		return new ReportingInsightReference("INVOICE", id, number, number);
	}

	public static ReportingInsightReference customer(UUID id, String name) {
		return new ReportingInsightReference("CUSTOMER", id, null, name);
	}
}
