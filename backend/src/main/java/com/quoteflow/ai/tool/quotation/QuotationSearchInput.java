package com.quoteflow.ai.tool.quotation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record QuotationSearchInput(
		@Size(max = 120)
		String query,

		@Size(max = 20)
		String status,

		@Size(max = 20)
		String period,

		@Min(1)
		@Max(20)
		Integer limit
) {
	public QuotationSearchInput {
		if (limit == null) {
			limit = 10;
		}
	}
}
