package com.quoteflow.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Currency;

public class IsoCurrencyCodeValidator implements ConstraintValidator<IsoCurrencyCode, String> {

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if (value == null || !value.matches("^[A-Z]{3}$")) {
			return false;
		}
		try {
			Currency.getInstance(value);
			return true;
		}
		catch (IllegalArgumentException ex) {
			return false;
		}
	}
}
