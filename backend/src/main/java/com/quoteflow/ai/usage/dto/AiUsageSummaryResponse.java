package com.quoteflow.ai.usage.dto;

import java.util.List;

public record AiUsageSummaryResponse(
		List<AiUsageFeatureDto> features
) {
}
