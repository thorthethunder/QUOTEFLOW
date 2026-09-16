package com.quoteflow.ai.exception;

public class AiConfigurationException extends AiException {

	public AiConfigurationException(String message) {
		super("AI_CONFIGURATION_ERROR", message);
	}
}
