package com.quoteflow.customer.dto;

import com.quoteflow.common.EmailNormalizer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record CreateCustomerRequest(
		@NotBlank @Size(max = 200) String displayName,
		@Email @Size(max = 320) String email,
		@Size(max = 40) String phone,
		@Size(max = 200) String companyName,
		@Size(max = 200) String addressLine1,
		@Size(max = 200) String addressLine2,
		@Size(max = 100) String city,
		@Size(max = 100) String stateRegion,
		@Size(max = 20) String postalCode,
		@Pattern(regexp = "^[A-Z]{2}$", message = "Must be a 2-letter ISO country code")
		String countryCode,
		@Size(max = 50) String taxId,
		@Size(max = 2000) String notes
) {
	public CreateCustomerRequest {
		displayName = blankToNull(displayName);
		email = EmailNormalizer.normalize(blankToNull(email));
		phone = blankToNull(phone);
		companyName = blankToNull(companyName);
		addressLine1 = blankToNull(addressLine1);
		addressLine2 = blankToNull(addressLine2);
		city = blankToNull(city);
		stateRegion = blankToNull(stateRegion);
		postalCode = blankToNull(postalCode);
		String country = blankToNull(countryCode);
		countryCode = country == null ? null : country.toUpperCase(Locale.ROOT);
		taxId = blankToNull(taxId);
		notes = blankToNull(notes);
	}

	private static String blankToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
