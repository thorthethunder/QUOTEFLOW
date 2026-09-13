package com.quoteflow.auth.dto;

import com.quoteflow.common.EmailNormalizer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
		@NotBlank @Email @Size(max = 320) String email,
		@NotBlank @Size(min = 1, max = 128) String password
) {
	public LoginRequest {
		email = EmailNormalizer.normalize(email);
	}
}
