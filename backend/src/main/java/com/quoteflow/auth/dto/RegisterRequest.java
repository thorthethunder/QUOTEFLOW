package com.quoteflow.auth.dto;

import com.quoteflow.common.EmailNormalizer;
import com.quoteflow.common.validation.IanaTimezone;
import com.quoteflow.common.validation.IsoCurrencyCode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
		@NotBlank @Size(max = 200) String businessName,
		@NotBlank @Size(max = 100) String firstName,
		@NotBlank @Size(max = 100) String lastName,
		@NotBlank @Email @Size(max = 320) String email,
		@NotBlank @Size(min = 12, max = 128) String password,
		@NotBlank @IanaTimezone @Size(max = 64) String timezone,
		@NotBlank @IsoCurrencyCode String currency
) {
	public RegisterRequest {
		businessName = businessName == null ? null : businessName.trim();
		firstName = firstName == null ? null : firstName.trim();
		lastName = lastName == null ? null : lastName.trim();
		email = EmailNormalizer.normalize(email);
		timezone = timezone == null ? null : timezone.trim();
		currency = currency == null ? null : currency.trim().toUpperCase();
	}
}
