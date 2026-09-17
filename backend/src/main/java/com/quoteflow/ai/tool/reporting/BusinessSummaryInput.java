package com.quoteflow.ai.tool.reporting;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record BusinessSummaryInput(
		@Size(max = 20)
		String period,

		LocalDate from,

		LocalDate to
) {
}
