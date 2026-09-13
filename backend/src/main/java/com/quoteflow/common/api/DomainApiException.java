package com.quoteflow.common.api;

import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * Domain exception mapped to a stable ApiError code.
 */
public class DomainApiException extends RuntimeException {

	private final String code;
	private final HttpStatus status;
	private final Map<String, Object> details;

	public DomainApiException(HttpStatus status, String code, String message) {
		this(status, code, message, null);
	}

	public DomainApiException(HttpStatus status, String code, String message, Map<String, Object> details) {
		super(message);
		this.status = status;
		this.code = code;
		this.details = details;
	}

	public String getCode() {
		return code;
	}

	public HttpStatus getStatus() {
		return status;
	}

	public Map<String, Object> getDetails() {
		return details;
	}
}
