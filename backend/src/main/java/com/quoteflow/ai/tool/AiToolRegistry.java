package com.quoteflow.ai.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Explicit allowlist of QuoteFlow AI tools. Fail closed: only tools registered here
 * and categorized {@link AiToolCategory#READ_ONLY} are exposed to the model.
 */
@Component
public class AiToolRegistry {

	private static final Logger log = LoggerFactory.getLogger(AiToolRegistry.class);

	private final Map<String, QuoteFlowAiTool> tools = new LinkedHashMap<>();
	private final ObjectMapper objectMapper;
	private final AiToolArgumentValidator argumentValidator;

	public AiToolRegistry(
			List<QuoteFlowAiTool> candidates,
			ObjectMapper objectMapper,
			AiToolArgumentValidator argumentValidator) {
		this.objectMapper = objectMapper;
		this.argumentValidator = argumentValidator;
		for (QuoteFlowAiTool tool : candidates) {
			if (tool.category() != AiToolCategory.READ_ONLY) {
				log.warn("ai.tool.skipped name={} category={} reason=not_read_only",
						tool.name(), tool.category());
				continue;
			}
			if (tools.containsKey(tool.name())) {
				throw new IllegalStateException("Duplicate AI tool registration: " + tool.name());
			}
			tools.put(tool.name(), tool);
			log.info("ai.tool.registered name={} category={}", tool.name(), tool.category());
		}
	}

	public List<String> allowlistedNames() {
		return List.copyOf(tools.keySet());
	}

	public QuoteFlowAiTool require(String name) {
		QuoteFlowAiTool tool = tools.get(name);
		if (tool == null) {
			throw new IllegalArgumentException("Tool not allowlisted: " + name);
		}
		return tool;
	}

	/**
	 * Builds Spring AI callbacks for the current allowlist only.
	 */
	@SuppressWarnings({"rawtypes", "unchecked"})
	public List<ToolCallback> springCallbacks() {
		List<ToolCallback> callbacks = new ArrayList<>();
		for (QuoteFlowAiTool tool : tools.values()) {
			BiFunction function = (input, toolContext) -> invoke(tool, input, (ToolContext) toolContext);
			FunctionToolCallback callback = FunctionToolCallback.builder(tool.name(), function)
					.description(tool.description())
					.inputType(tool.inputType())
					.build();
			callbacks.add(callback);
		}
		return List.copyOf(callbacks);
	}

	Object invoke(QuoteFlowAiTool tool, Object rawInput, ToolContext springContext) {
		CopilotToolContext ctx = extractContext(springContext);
		ctx.incrementAndGetToolCalls();
		Object input = coerceInput(rawInput, tool.inputType());
		argumentValidator.requireValid(input);
		return tool.execute(input, ctx);
	}

	private Object coerceInput(Object rawInput, Class<?> inputType) {
		if (rawInput == null) {
			try {
				return inputType.getDeclaredConstructor().newInstance();
			} catch (Exception ex) {
				return null;
			}
		}
		if (inputType.isInstance(rawInput)) {
			return rawInput;
		}
		return objectMapper.convertValue(rawInput, inputType);
	}

	public static CopilotToolContext extractContext(ToolContext springContext) {
		if (springContext == null || springContext.getContext() == null) {
			throw new IllegalStateException("Missing Copilot tool execution context");
		}
		Object value = springContext.getContext().get(CopilotToolContext.TOOL_CONTEXT_KEY);
		if (!(value instanceof CopilotToolContext ctx)) {
			throw new IllegalStateException("Invalid Copilot tool execution context");
		}
		return ctx;
	}
}
