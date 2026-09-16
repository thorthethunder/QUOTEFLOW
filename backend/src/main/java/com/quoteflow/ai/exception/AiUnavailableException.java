package com.quoteflow.ai.exception;

public class AiUnavailableException extends AiException {

	public AiUnavailableException(String message) {
		super("AI_UNAVAILABLE", message);
	}

	public AiUnavailableException(String message, Throwable cause) {
		super("AI_UNAVAILABLE", message, cause);
	}
}
