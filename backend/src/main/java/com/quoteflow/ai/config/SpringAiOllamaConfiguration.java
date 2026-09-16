package com.quoteflow.ai.config;

import com.quoteflow.ai.provider.spring.SpringAiOllamaProvider;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.ollama.management.PullModelStrategy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

/**
 * Manual Spring AI Ollama wiring when QuoteFlow AI is enabled.
 * Autoconfig chat model stays off ({@code spring.ai.model.chat=none}) so AI_ENABLED=false
 * never requires Ollama at startup.
 */
@Configuration
@ConditionalOnProperty(prefix = "quoteflow.ai", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "quoteflow.ai", name = "provider", havingValue = "OLLAMA", matchIfMissing = true)
@ConditionalOnProperty(prefix = "quoteflow.ai", name = "adapter", havingValue = "spring-ai", matchIfMissing = true)
public class SpringAiOllamaConfiguration {

	@Bean
	OllamaApi ollamaApi(AiProperties properties) {
		Duration connect = properties.getOllama().getConnectTimeout() == null
				? Duration.ofSeconds(5)
				: properties.getOllama().getConnectTimeout();
		Duration read = properties.getOllama().getReadTimeout() == null
				? Duration.ofSeconds(120)
				: properties.getOllama().getReadTimeout();
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connect).build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
		factory.setReadTimeout(read);
		RestClient.Builder restClientBuilder = RestClient.builder().requestFactory(factory);
		return OllamaApi.builder()
				.baseUrl(trimSlash(properties.getOllama().getBaseUrl()))
				.restClientBuilder(restClientBuilder)
				.build();
	}

	@Bean
	OllamaChatModel ollamaChatModel(OllamaApi ollamaApi, AiProperties properties) {
		OllamaChatOptions options = OllamaChatOptions.builder()
				.model(properties.getOllama().getModel())
				.temperature(0.1)
				.disableThinking()
				.build();
		ModelManagementOptions management = ModelManagementOptions.builder()
				.pullModelStrategy(PullModelStrategy.NEVER)
				.additionalModels(List.of())
				.build();
		ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder().build();
		return OllamaChatModel.builder()
				.ollamaApi(ollamaApi)
				.options(options)
				.toolCallingManager(toolCallingManager)
				.observationRegistry(ObservationRegistry.NOOP)
				.modelManagementOptions(management)
				.build();
	}

	@Bean
	ChatClient quoteFlowChatClient(OllamaChatModel ollamaChatModel) {
		return ChatClient.create(ollamaChatModel);
	}

	@Bean
	SpringAiOllamaProvider springAiOllamaProvider(
			ChatClient quoteFlowChatClient,
			OllamaApi ollamaApi,
			AiProperties properties,
			com.quoteflow.ai.structured.StructuredOutputValidator structuredOutputValidator,
			com.quoteflow.ai.usage.AiUsageRecorder usageRecorder) {
		return new SpringAiOllamaProvider(
				quoteFlowChatClient,
				ollamaApi,
				properties,
				structuredOutputValidator,
				usageRecorder);
	}

	private static String trimSlash(String url) {
		if (url == null || url.isBlank()) {
			throw new IllegalStateException("OLLAMA_BASE_URL is required when AI is enabled");
		}
		String trimmed = url.trim();
		while (trimmed.endsWith("/")) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed;
	}
}
