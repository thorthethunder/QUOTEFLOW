package com.quoteflow.ai.exception;

/**
 * Base type for QuoteFlow AI subsystem failures. Safe for internal handling;
 * do not expose provider stack traces to API clients.
 */
public class AiException extends RuntimeException {

	private final String code;

	public AiException(String code, String message) {
		super(message);
		this.code = code;
	}

	public AiException(String code, String message, Throwable cause) {
		super(message, cause);
		this.code = code;
	}

	public String getCode() {
		return code;
	}
}
