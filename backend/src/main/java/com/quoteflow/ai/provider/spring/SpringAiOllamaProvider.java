package com.quoteflow.ai.provider.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quoteflow.ai.config.AiProperties;
import com.quoteflow.ai.exception.AiException;
import com.quoteflow.ai.exception.AiInvalidResponseException;
import com.quoteflow.ai.exception.AiTimeoutException;
import com.quoteflow.ai.exception.AiUnavailableException;
import com.quoteflow.ai.provider.AiFeature;
import com.quoteflow.ai.provider.AiGenerationOptions;
import com.quoteflow.ai.provider.AiProvider;
import com.quoteflow.ai.provider.AiProviderType;
import com.quoteflow.ai.provider.AiRequest;
import com.quoteflow.ai.provider.AiResponse;
import com.quoteflow.ai.structured.StructuredAiRequest;
import com.quoteflow.ai.structured.StructuredAiResponse;
import com.quoteflow.ai.structured.StructuredOutputValidator;
import com.quoteflow.ai.usage.AiUsageEvent;
import com.quoteflow.ai.usage.AiUsageRecorder;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.web.client.ResourceAccessException;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

/**
 * Spring AI–backed Ollama {@link AiProvider}. Business modules depend only on {@link AiProvider}.
 */
public class SpringAiOllamaProvider implements AiProvider {

	private final ChatClient chatClient;
	private final OllamaApi ollamaApi;
	private final AiProperties properties;
	private final StructuredOutputValidator structuredOutputValidator;
	private final AiUsageRecorder usageRecorder;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public SpringAiOllamaProvider(
			ChatClient chatClient,
			OllamaApi ollamaApi,
			AiProperties properties,
			StructuredOutputValidator structuredOutputValidator,
			AiUsageRecorder usageRecorder) {
		this.chatClient = chatClient;
		this.ollamaApi = ollamaApi;
		this.properties = properties;
		this.structuredOutputValidator = structuredOutputValidator;
		this.usageRecorder = usageRecorder;
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
		return properties.getOllama().getModel();
	}

	@Override
	public boolean isEnabled() {
		return properties.isEnabled();
	}

	@Override
	public boolean isAvailable() {
		try {
			ollamaApi.listModels();
			return true;
		} catch (Exception ex) {
			return false;
		}
	}

	@Override
	public AiResponse generate(AiRequest request) {
		long start = System.nanoTime();
		try {
			var promptSpec = chatClient.prompt()
					.system(request.systemPrompt() == null ? "" : request.systemPrompt())
					.user(request.userPrompt())
					.options(buildOptionsBuilder(request.options(), null));
			ChatResponse response = promptSpec.call().chatResponse();
			long latencyMs = (System.nanoTime() - start) / 1_000_000L;
			String content = extractContent(response);
			if (content == null || content.isBlank()) {
				recordFailure(request.feature(), request, "AI_INVALID_RESPONSE", latencyMs, null, null);
				throw new AiInvalidResponseException("Empty model response");
			}
			Integer in = promptTokens(response);
			Integer out = completionTokens(response);
			recordSuccess(request.feature(), request.businessId(), request.userId(), latencyMs, in, out);
			return new AiResponse(content, providerName(), modelName(response), in, out, latencyMs);
		} catch (AiException ex) {
			recordFailure(request.feature(), request, ex.getCode(), 0L, null, null);
			throw ex;
		} catch (Exception ex) {
			throw mapException(ex);
		}
	}

	@Override
	public <T> StructuredAiResponse<T> generateStructured(StructuredAiRequest<T> request) {
		long start = System.nanoTime();
		try {
			Map<?, ?> schemaMap = objectMapper.convertValue(request.jsonSchema(), Map.class);
			ChatResponse response = chatClient.prompt()
					.system(request.systemPrompt() == null
							? "Return only valid JSON matching the schema. No markdown."
							: request.systemPrompt())
					.user(request.userPrompt())
					.options(buildOptionsBuilder(request.options(), schemaMap))
					.call()
					.chatResponse();
			long latencyMs = (System.nanoTime() - start) / 1_000_000L;
			String content = extractContent(response);
			T value = structuredOutputValidator.validateAndParse(content, request.responseType());
			Integer in = promptTokens(response);
			Integer out = completionTokens(response);
			recordSuccess(request.feature(), request.businessId(), request.userId(), latencyMs, in, out);
			return new StructuredAiResponse<>(
					value, content, providerName(), modelName(response), in, out, latencyMs);
		} catch (AiException ex) {
			recordFailure(
					request.feature(),
					new AiRequest(
							request.systemPrompt(),
							request.userPrompt(),
							request.feature(),
							request.options(),
							request.businessId(),
							request.userId()),
					ex.getCode(),
					0L,
					null,
					null);
			throw ex;
		} catch (Exception ex) {
			throw mapException(ex);
		}
	}

	private OllamaChatOptions.Builder buildOptionsBuilder(AiGenerationOptions options, Map<?, ?> formatSchema) {
		var builder = OllamaChatOptions.builder()
				.model(model())
				.disableThinking();
		if (options != null && options.temperature() != null) {
			builder.temperature(options.temperature());
		} else {
			builder.temperature(0.1);
		}
		if (options != null && options.numPredict() != null) {
			builder.numPredict(options.numPredict());
		}
		if (options == null || options.disableThinking()) {
			builder.disableThinking();
		}
		if (formatSchema != null) {
			builder.format(formatSchema);
		}
		return builder;
	}

	private static String extractContent(ChatResponse response) {
		if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
			return "";
		}
		return response.getResult().getOutput().getText();
	}

	private String modelName(ChatResponse response) {
		if (response != null && response.getMetadata() != null && response.getMetadata().getModel() != null) {
			return response.getMetadata().getModel();
		}
		return model();
	}

	private static Integer promptTokens(ChatResponse response) {
		Usage usage = usage(response);
		return usage == null || usage.getPromptTokens() == null ? null : usage.getPromptTokens().intValue();
	}

	private static Integer completionTokens(ChatResponse response) {
		Usage usage = usage(response);
		return usage == null || usage.getCompletionTokens() == null ? null : usage.getCompletionTokens().intValue();
	}

	private static Usage usage(ChatResponse response) {
		return response == null || response.getMetadata() == null ? null : response.getMetadata().getUsage();
	}

	private AiException mapException(Exception ex) {
		Throwable cause = ex;
		while (cause.getCause() != null && cause.getCause() != cause) {
			cause = cause.getCause();
		}
		if (cause instanceof TimeoutException
				|| (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("timed out"))
				|| cause instanceof ResourceAccessException) {
			return new AiTimeoutException("Ollama request timed out", ex);
		}
		if (ex instanceof ResourceAccessException || cause instanceof java.net.ConnectException) {
			return new AiUnavailableException("Ollama unavailable", ex);
		}
		return new AiUnavailableException("AI provider request failed", ex);
	}

	private void recordSuccess(
			AiFeature feature, UUID businessId, UUID userId, long latencyMs, Integer in, Integer out) {
		usageRecorder.record(new AiUsageEvent(
				AiProviderType.OLLAMA,
				providerName(),
				model(),
				feature,
				true,
				null,
				latencyMs,
				in,
				out,
				businessId,
				userId));
	}

	private void recordFailure(
			AiFeature feature, AiRequest request, String code, long latencyMs, Integer in, Integer out) {
		usageRecorder.record(new AiUsageEvent(
				AiProviderType.OLLAMA,
				providerName(),
				model(),
				feature,
				false,
				code,
				latencyMs,
				in,
				out,
				request.businessId(),
				request.userId()));
	}
}
