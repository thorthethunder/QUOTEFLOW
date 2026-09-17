package com.quoteflow.ai.tool;

/**
 * QuoteFlow-owned AI tool contract. Only explicitly registered READ_ONLY tools
 * are exposed to Spring AI — never arbitrary {@code @Service} methods.
 */
public interface QuoteFlowAiTool {

	String name();

	String description();

	AiToolCategory category();

	Class<?> inputType();

	Object execute(Object input, CopilotToolContext context);
}
