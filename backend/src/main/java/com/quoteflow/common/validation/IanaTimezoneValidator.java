package com.quoteflow.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.DateTimeException;
import java.time.ZoneId;

public class IanaTimezoneValidator implements ConstraintValidator<IanaTimezone, String> {

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if (value == null || value.isBlank()) {
			return false;
		}
		try {
			ZoneId.of(value);
			return true;
		}
		catch (DateTimeException ex) {
			return false;
		}
	}
}
