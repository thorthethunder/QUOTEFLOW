package com.quoteflow.ai.provider;

import com.quoteflow.ai.exception.AiUnavailableException;
import com.quoteflow.ai.structured.StructuredAiRequest;
import com.quoteflow.ai.structured.StructuredAiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Active when AI is disabled (default). Core SaaS readiness does not depend on this bean.
 */
@Component
@ConditionalOnProperty(prefix = "quoteflow.ai", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DisabledAiProvider implements AiProvider {

	@Override
	public AiProviderType type() {
		return AiProviderType.DISABLED;
	}

	@Override
	public String providerName() {
		return "DISABLED";
	}

	@Override
	public String model() {
		return "";
	}

	@Override
	public boolean isEnabled() {
		return false;
	}

	@Override
	public boolean isAvailable() {
		return false;
	}

	@Override
	public AiResponse generate(AiRequest request) {
		throw new AiUnavailableException("AI is disabled");
	}

	@Override
	public <T> StructuredAiResponse<T> generateStructured(StructuredAiRequest<T> request) {
		throw new AiUnavailableException("AI is disabled");
	}
}
