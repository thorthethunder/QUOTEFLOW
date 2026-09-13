package com.quoteflow.invoice.dto;

import java.util.List;

public record PagedInvoiceResponse(
		List<InvoiceSummaryResponse> content,
		int page,
		int size,
		long totalElements,
		int totalPages
) {
}
