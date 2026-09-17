package com.quoteflow.ai.tool.customer;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record CustomerLookupInput(
		@Size(max = 120)
		String query,

		@Min(1)
		@Max(20)
		Integer limit
) {
	public CustomerLookupInput {
		if (limit == null) {
			limit = 10;
		}
	}
}
