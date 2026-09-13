package com.quoteflow.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
		Instant timestamp,
		int status,
		String code,
		String message,
		String path,
		String correlationId,
		List<FieldViolation> validationErrors,
		Map<String, Object> details
) {
	public record FieldViolation(String field, String message) {
	}
}
