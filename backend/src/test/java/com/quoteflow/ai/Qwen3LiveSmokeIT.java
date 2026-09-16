package com.quoteflow.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quoteflow.ai.config.AiProperties;
import com.quoteflow.ai.provider.AiFeature;
import com.quoteflow.ai.provider.AiGenerationOptions;
import com.quoteflow.ai.provider.spring.SpringAiOllamaProvider;
import com.quoteflow.ai.structured.StructuredAiRequest;
import com.quoteflow.ai.structured.StructuredAiResponse;
import com.quoteflow.ai.structured.StructuredOutputValidator;
import com.quoteflow.ai.structured.demo.QuotationDraftProposal;
import io.micrometer.observation.ObservationRegistry;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.ollama.management.PullModelStrategy;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Optional live smoke against local Ollama + qwen3:8b through Spring AI AiProvider path.
 * Run: set QUOTEFLOW_AI_LIVE=true && mvnw -Dtest=Qwen3LiveSmokeIT test
 */
@EnabledIfEnvironmentVariable(named = "QUOTEFLOW_AI_LIVE", matches = "true")
class Qwen3LiveSmokeIT {

	@Test
	void structuredExtractionThroughSpringAiProvider() {
		String baseUrl = System.getenv().getOrDefault("OLLAMA_BASE_URL", "http://localhost:11434");
		String modelName = System.getenv().getOrDefault("OLLAMA_MODEL", "qwen3:8b");
		Assumptions.assumeTrue(isOllamaUp(baseUrl), "Ollama not reachable");

		AiProperties props = new AiProperties();
		props.setEnabled(true);
		props.getOllama().setBaseUrl(baseUrl);
		props.getOllama().setModel(modelName);
		props.getOllama().setConnectTimeout(Duration.ofSeconds(5));
		props.getOllama().setReadTimeout(Duration.ofSeconds(180));

		Duration connect = props.getOllama().getConnectTimeout();
		Duration read = props.getOllama().getReadTimeout();
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connect).build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
		factory.setReadTimeout(read);
		OllamaApi api = OllamaApi.builder()
				.baseUrl(baseUrl)
				.restClientBuilder(RestClient.builder().requestFactory(factory))
				.build();
		OllamaChatModel chatModel = OllamaChatModel.builder()
				.ollamaApi(api)
				.options(OllamaChatOptions.builder().model(modelName).temperature(0.1).disableThinking().build())
				.toolCallingManager(DefaultToolCallingManager.builder().build())
				.observationRegistry(ObservationRegistry.NOOP)
				.modelManagementOptions(ModelManagementOptions.builder()
						.pullModelStrategy(PullModelStrategy.NEVER)
						.additionalModels(List.of())
						.build())
				.build();
		ChatClient chatClient = ChatClient.create(chatModel);
		ObjectMapper mapper = new ObjectMapper();
		SpringAiOllamaProvider provider = new SpringAiOllamaProvider(
				chatClient,
				api,
				props,
				new StructuredOutputValidator(mapper, Validation.buildDefaultValidatorFactory().getValidator()),
				event -> {
				});

		long start = System.currentTimeMillis();
		StructuredAiResponse<QuotationDraftProposal> result = provider.generateStructured(
				new StructuredAiRequest<>(
						"Extract quotation draft JSON only. Do not calculate totals. No markdown.",
						"Create a quotation draft for Raj Electrical for 2 ceiling fans at 3000 each, 5 switches at 250 each, wiring work at 1800 and labour at 2500.",
						QuotationDraftProposal.class,
						QuotationDraftProposal.jsonSchema(mapper),
						AiFeature.STRUCTURED_SMOKE,
						AiGenerationOptions.structuredExtraction(),
						null,
						null));
		long latency = System.currentTimeMillis() - start;

		assertThat(result.value().customerName()).containsIgnoringCase("Raj");
		assertThat(result.value().items()).hasSizeGreaterThanOrEqualTo(4);
		assertThat(result.value().items()).anySatisfy(i -> {
			assertThat(i.description().toLowerCase()).containsAnyOf("fan", "ceiling");
			assertThat(i.quantity()).isEqualByComparingTo("2");
			assertThat(i.unitPrice()).isEqualByComparingTo("3000");
		});
		System.out.println("qwen3.live.smoke latencyMs=" + latency + " model=" + result.model());
	}

	private static boolean isOllamaUp(String baseUrl) {
		try {
			OllamaApi.builder().baseUrl(baseUrl).build().listModels();
			return true;
		} catch (Exception ex) {
			return false;
		}
	}
}
