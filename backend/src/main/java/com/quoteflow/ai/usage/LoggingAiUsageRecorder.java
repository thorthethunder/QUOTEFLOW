package com.quoteflow.ai.usage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Logs AI usage metadata without prompts, responses, or PII payloads.
 */
@Component
public class LoggingAiUsageRecorder implements AiUsageRecorder {

	private static final Logger log = LoggerFactory.getLogger(LoggingAiUsageRecorder.class);

	@Override
	public void record(AiUsageEvent event) {
		int in = event.inputTokens() == null ? -1 : event.inputTokens();
		int out = event.outputTokens() == null ? -1 : event.outputTokens();
		int tools = event.toolCallCount() == null ? -1 : event.toolCallCount();
		log.info(
				"ai.usage provider={} model={} feature={} success={} errorCode={} latencyMs={} inputTokens={} outputTokens={} toolCalls={} businessIdPresent={} userIdPresent={}",
				event.providerName(),
				event.model(),
				event.feature(),
				event.success(),
				event.errorCode() == null ? "-" : event.errorCode(),
				event.latencyMs(),
				in,
				out,
				tools,
				event.businessId() != null,
				event.userId() != null);
	}
}
