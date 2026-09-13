package com.quoteflow.customer.dto;

import com.quoteflow.customer.Customer;
import com.quoteflow.customer.CustomerStatus;

import java.time.Instant;
import java.util.UUID;

public record CustomerSummaryResponse(
		UUID id,
		String displayName,
		String companyName,
		String email,
		String phone,
		CustomerStatus status,
		Instant updatedAt
) {
	public static CustomerSummaryResponse from(Customer customer) {
		return new CustomerSummaryResponse(
				customer.getId(),
				customer.getDisplayName(),
				customer.getCompanyName(),
				customer.getEmail(),
				customer.getPhone(),
				customer.getStatus(),
				customer.getUpdatedAt());
	}
}
