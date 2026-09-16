package com.quoteflow.ai.provider.ollama;

import com.quoteflow.ai.config.AiProperties;
import com.quoteflow.ai.exception.AiException;
import com.quoteflow.ai.exception.AiInvalidResponseException;
import com.quoteflow.ai.provider.AiFeature;
import com.quoteflow.ai.provider.AiProvider;
import com.quoteflow.ai.provider.AiProviderType;
import com.quoteflow.ai.provider.AiRequest;
import com.quoteflow.ai.provider.AiResponse;
import com.quoteflow.ai.structured.StructuredAiRequest;
import com.quoteflow.ai.structured.StructuredAiResponse;
import com.quoteflow.ai.structured.StructuredOutputValidator;
import com.quoteflow.ai.usage.AiUsageEvent;
import com.quoteflow.ai.usage.AiUsageRecorder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Ollama-backed {@link AiProvider}. No business repository access.
 */
@Component
@ConditionalOnProperty(prefix = "quoteflow.ai", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "quoteflow.ai", name = "provider", havingValue = "OLLAMA", matchIfMissing = true)
public class OllamaAiProvider implements AiProvider {

	private final OllamaClient client;
	private final StructuredOutputValidator structuredOutputValidator;
	private final AiUsageRecorder usageRecorder;
	private final AiProperties properties;

	public OllamaAiProvider(
			OllamaClient client,
			StructuredOutputValidator structuredOutputValidator,
			AiUsageRecorder usageRecorder,
			AiProperties properties) {
		this.client = client;
		this.structuredOutputValidator = structuredOutputValidator;
		this.usageRecorder = usageRecorder;
		this.properties = properties;
	}

	@Override
	public AiProviderType type() {
		return AiProviderType.OLLAMA;
	}

	@Override
	public String providerName() {
		return "OLLAMA";
	}

	@Override
	public String model() {
		return client.model();
	}

	@Override
	public boolean isEnabled() {
		return properties.isEnabled();
	}

	@Override
	public boolean isAvailable() {
		return client.isReachable();
	}

	@Override
	public AiResponse generate(AiRequest request) {
		try {
			OllamaClient.ChatResult result = client.chat(
					request.systemPrompt(),
					request.userPrompt(),
					null,
					request.options());
			if (result.content() == null || result.content().isBlank()) {
				recordFailure(request.feature(), request, "AI_INVALID_RESPONSE", result.latencyMs(), result);
				throw new AiInvalidResponseException("Empty model response");
			}
			recordSuccess(request.feature(), request.businessId(), request.userId(), result);
			return new AiResponse(
					result.content(),
					providerName(),
					result.model(),
					result.inputTokens(),
					result.outputTokens(),
					result.latencyMs());
		} catch (AiException ex) {
			recordFailure(request.feature(), request, ex.getCode(), 0L, null);
			throw ex;
		}
	}

	@Override
	public <T> StructuredAiResponse<T> generateStructured(StructuredAiRequest<T> request) {
		try {
			OllamaClient.ChatResult result = client.chat(
					request.systemPrompt(),
					request.userPrompt(),
					request.jsonSchema(),
					request.options());
			T value = structuredOutputValidator.validateAndParse(result.content(), request.responseType());
			recordSuccess(request.feature(), request.businessId(), request.userId(), result);
			return new StructuredAiResponse<>(
					value,
					result.content(),
					providerName(),
					result.model(),
					result.inputTokens(),
					result.outputTokens(),
					result.latencyMs());
		} catch (AiException ex) {
			recordFailure(request.feature(),
					new AiRequest(request.systemPrompt(), request.userPrompt(), request.feature(),
							request.options(), request.businessId(), request.userId()),
					ex.getCode(),
					0L,
					null);
			throw ex;
		}
	}

	private void recordSuccess(AiFeature feature, java.util.UUID businessId, java.util.UUID userId,
			OllamaClient.ChatResult result) {
		usageRecorder.record(new AiUsageEvent(
				AiProviderType.OLLAMA,
				providerName(),
				result.model(),
				feature,
				true,
				null,
				result.latencyMs(),
				result.inputTokens(),
				result.outputTokens(),
				businessId,
				userId));
	}

	private void recordFailure(
			AiFeature feature,
			AiRequest request,
			String code,
			long latencyMs,
			OllamaClient.ChatResult result) {
		usageRecorder.record(new AiUsageEvent(
				AiProviderType.OLLAMA,
				providerName(),
				result != null ? result.model() : model(),
				feature,
				false,
				code,
				result != null ? result.latencyMs() : latencyMs,
				result != null ? result.inputTokens() : null,
				result != null ? result.outputTokens() : null,
				request.businessId(),
				request.userId()));
	}
}
