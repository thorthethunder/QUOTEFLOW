package com.quoteflow.quotation.dto;

import java.util.List;

public record PagedQuotationResponse(
		List<QuotationSummaryResponse> content,
		int page,
		int size,
		long totalElements,
		int totalPages
) {
}
