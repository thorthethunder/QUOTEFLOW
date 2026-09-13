package com.quoteflow.customer.dto;

import com.quoteflow.customer.Customer;
import com.quoteflow.customer.CustomerStatus;

import java.time.Instant;
import java.util.UUID;

public record CustomerResponse(
		UUID id,
		String displayName,
		String email,
		String phone,
		String companyName,
		String addressLine1,
		String addressLine2,
		String city,
		String stateRegion,
		String postalCode,
		String countryCode,
		String taxId,
		String notes,
		CustomerStatus status,
		Instant createdAt,
		Instant updatedAt
) {
	public static CustomerResponse from(Customer customer) {
		return new CustomerResponse(
				customer.getId(),
				customer.getDisplayName(),
				customer.getEmail(),
				customer.getPhone(),
				customer.getCompanyName(),
				customer.getAddressLine1(),
				customer.getAddressLine2(),
				customer.getCity(),
				customer.getStateRegion(),
				customer.getPostalCode(),
				customer.getCountryCode(),
				customer.getTaxId(),
				customer.getNotes(),
				customer.getStatus(),
				customer.getCreatedAt(),
				customer.getUpdatedAt());
	}
}
