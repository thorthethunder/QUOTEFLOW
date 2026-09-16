package com.quoteflow.ai.structured;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quoteflow.ai.exception.AiInvalidResponseException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates AI structured output as untrusted input: size, JSON parse, bean validation.
 */
public class StructuredOutputValidator {

	private final ObjectMapper objectMapper;
	private final Validator validator;
	private final int maxResponseChars;

	public StructuredOutputValidator(ObjectMapper objectMapper, Validator validator) {
		this(objectMapper, validator, 256_000);
	}

	public StructuredOutputValidator(ObjectMapper objectMapper, Validator validator, int maxResponseChars) {
		this.objectMapper = objectMapper;
		this.validator = validator;
		this.maxResponseChars = maxResponseChars;
	}

	public <T> T validateAndParse(String raw, Class<T> type) {
		if (raw == null || raw.isBlank()) {
			throw new AiInvalidResponseException("AI response was empty");
		}
		if (raw.length() > maxResponseChars) {
			throw new AiInvalidResponseException("AI response exceeds max size");
		}
		final JsonNode tree;
		try {
			tree = objectMapper.readTree(raw);
		} catch (Exception ex) {
			throw new AiInvalidResponseException("AI response is not valid JSON", ex);
		}
		if (tree == null || !tree.isObject()) {
			throw new AiInvalidResponseException("AI response must be a JSON object");
		}
		final T value;
		try {
			value = objectMapper.treeToValue(tree, type);
		} catch (Exception ex) {
			throw new AiInvalidResponseException("AI response does not match expected schema", ex);
		}
		if (value == null) {
			throw new AiInvalidResponseException("AI response mapped to null");
		}
		Set<ConstraintViolation<T>> violations = validator.validate(value);
		if (!violations.isEmpty()) {
			String detail = violations.stream()
					.map(v -> v.getPropertyPath() + ": " + v.getMessage())
					.collect(Collectors.joining("; "));
			throw new AiInvalidResponseException("AI response failed validation: " + detail);
		}
		return value;
	}
}
