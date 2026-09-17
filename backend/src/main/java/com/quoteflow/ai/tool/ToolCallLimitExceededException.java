package com.quoteflow.ai.tool;

import com.quoteflow.ai.exception.AiException;

public class ToolCallLimitExceededException extends AiException {

	public ToolCallLimitExceededException(String message) {
		super("AI_TOOL_LIMIT", message);
	}
}
