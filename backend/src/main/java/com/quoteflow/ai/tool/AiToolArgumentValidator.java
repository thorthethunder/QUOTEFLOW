package com.quoteflow.ai.tool;

import com.quoteflow.common.api.DomainApiException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class AiToolArgumentValidator {

	private final Validator validator;

	public AiToolArgumentValidator(Validator validator) {
		this.validator = validator;
	}

	public <T> T requireValid(T input) {
		if (input == null) {
			throw new DomainApiException(HttpStatus.BAD_REQUEST, "AI_TOOL_ARGS_INVALID",
					"Tool arguments are required");
		}
		Set<ConstraintViolation<T>> violations = validator.validate(input);
		if (!violations.isEmpty()) {
			String detail = violations.stream()
					.map(v -> v.getPropertyPath() + ": " + v.getMessage())
					.collect(Collectors.joining("; "));
			throw new DomainApiException(HttpStatus.BAD_REQUEST, "AI_TOOL_ARGS_INVALID",
					"Invalid tool arguments: " + detail);
		}
		return input;
	}
}
