package com.quoteflow.ai.usage.dto;

import java.time.Instant;

public record AiUsageFeatureDto(
		String feature,
		boolean entitled,
		int used,
		int limit,
		int remaining,
		String period,
		Instant resetAt
) {
}
