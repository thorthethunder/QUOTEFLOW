package com.quoteflow.ai.usage;

import java.time.Instant;

public record AiUsagePeriod(
		String key,
		Instant startInclusive,
		Instant endExclusive
) {
}
