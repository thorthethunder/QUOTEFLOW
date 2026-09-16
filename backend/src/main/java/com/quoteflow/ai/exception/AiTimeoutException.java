package com.quoteflow.ai.exception;

public class AiTimeoutException extends AiException {

	public AiTimeoutException(String message) {
		super("AI_TIMEOUT", message);
	}

	public AiTimeoutException(String message, Throwable cause) {
		super("AI_TIMEOUT", message, cause);
	}
}
