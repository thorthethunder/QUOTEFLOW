package com.quoteflow.ai.exception;

public class AiInvalidResponseException extends AiException {

	public AiInvalidResponseException(String message) {
		super("AI_INVALID_RESPONSE", message);
	}

	public AiInvalidResponseException(String message, Throwable cause) {
		super("AI_INVALID_RESPONSE", message, cause);
	}
}
