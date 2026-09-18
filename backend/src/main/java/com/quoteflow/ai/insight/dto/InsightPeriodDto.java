package com.quoteflow.ai.insight.dto;

import java.time.LocalDate;

public record InsightPeriodDto(
		String label,
		LocalDate from,
		LocalDate to,
		String timezone
) {
}
