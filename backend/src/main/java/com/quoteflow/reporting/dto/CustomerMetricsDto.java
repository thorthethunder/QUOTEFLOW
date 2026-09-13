package com.quoteflow.reporting.dto;

public record CustomerMetricsDto(
		long activeCount,
		long archivedCount,
		long newInPeriodCount
) {
}
