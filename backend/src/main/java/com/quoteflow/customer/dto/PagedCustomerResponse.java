package com.quoteflow.customer.dto;

import java.util.List;

public record PagedCustomerResponse(
		List<CustomerSummaryResponse> content,
		int page,
		int size,
		long totalElements,
		int totalPages
) {
}
