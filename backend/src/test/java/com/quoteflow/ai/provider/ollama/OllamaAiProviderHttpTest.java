package com.quoteflow.ai.provider.ollama;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quoteflow.ai.config.AiProperties;
import com.quoteflow.ai.exception.AiInvalidResponseException;
import com.quoteflow.ai.exception.AiTimeoutException;
import com.quoteflow.ai.exception.AiUnavailableException;
import com.quoteflow.ai.provider.AiFeature;
import com.quoteflow.ai.provider.AiGenerationOptions;
import com.quoteflow.ai.provider.AiRequest;
import com.quoteflow.ai.provider.AiResponse;
import com.quoteflow.ai.structured.StructuredAiRequest;
import com.quoteflow.ai.structured.StructuredAiResponse;
import com.quoteflow.ai.structured.StructuredOutputValidator;
import com.quoteflow.ai.structured.demo.QuotationDraftProposal;
import com.quoteflow.ai.usage.AiUsageEvent;
import com.quoteflow.ai.usage.AiUsageRecorder;
import com.sun.net.httpserver.HttpServer;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OllamaAiProviderHttpTest {

	private HttpServer server;
	private String baseUrl;
	private final AtomicReference<String> chatResponse = new AtomicReference<>();
	private final AtomicInteger chatStatus = new AtomicInteger(200);
	private final AtomicInteger tagsStatus = new AtomicInteger(200);
	private final List<AiUsageEvent> usage = new ArrayList<>();
	private OllamaAiProvider provider;
	private ObjectMapper mapper;

	@BeforeEach
	void startServer() throws IOException {
		mapper = new ObjectMapper();
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/api/tags", exchange -> {
			byte[] body = "{\"models\":[{\"name\":\"qwen3:8b\"}]}".getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(tagsStatus.get(), body.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(body);
			}
		});
		server.createContext("/api/chat", exchange -> {
			byte[] req = exchange.getRequestBody().readAllBytes();
			assertThat(new String(req, StandardCharsets.UTF_8)).doesNotContain("providerUrl");
			byte[] body = chatResponse.get().getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(chatStatus.get(), body.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(body);
			}
		});
		server.setExecutor(Executors.newCachedThreadPool());
		server.start();
		baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

		AiProperties props = new AiProperties();
		props.setEnabled(true);
		props.setProvider("OLLAMA");
		props.getOllama().setBaseUrl(baseUrl);
		props.getOllama().setModel("qwen3:8b");
		props.getOllama().setConnectTimeout(java.time.Duration.ofSeconds(2));
		props.getOllama().setReadTimeout(java.time.Duration.ofSeconds(3));

		OllamaClient client = new OllamaClient(props, mapper);
		StructuredOutputValidator validator = new StructuredOutputValidator(
				mapper,
				Validation.buildDefaultValidatorFactory().getValidator(),
				256_000);
		AiUsageRecorder recorder = usage::add;
		provider = new OllamaAiProvider(client, validator, recorder, props);
	}

	@AfterEach
	void stop() {
		if (server != null) {
			server.stop(0);
		}
	}

	@Test
	void textGenerationSuccess() {
		chatResponse.set("""
				{"model":"qwen3:8b","message":{"role":"assistant","content":"hello"},"prompt_eval_count":3,"eval_count":2}
				""");
		AiResponse response = provider.generate(AiRequest.of(AiFeature.PROVIDER_SMOKE, "Say hello"));
		assertThat(response.content()).isEqualTo("hello");
		assertThat(response.model()).isEqualTo("qwen3:8b");
		assertThat(usage).hasSize(1);
		assertThat(usage.getFirst().success()).isTrue();
	}

	@Test
	void structuredGenerationSuccess() {
		chatResponse.set("""
				{"model":"qwen3:8b","message":{"role":"assistant","content":"{\\"customerName\\":\\"Raj Electrical\\",\\"items\\":[{\\"description\\":\\"ceiling fans\\",\\"quantity\\":2,\\"unitPrice\\":3000},{\\"description\\":\\"switches\\",\\"quantity\\":5,\\"unitPrice\\":250},{\\"description\\":\\"wiring work\\",\\"quantity\\":1,\\"unitPrice\\":1800},{\\"description\\":\\"labour\\",\\"quantity\\":1,\\"unitPrice\\":2500}]}"},"prompt_eval_count":10,"eval_count":40}
				""");
		var request = new StructuredAiRequest<>(
				"Extract quotation draft JSON only.",
				"Create a quotation draft for Raj Electrical for 2 ceiling fans at 3000 each.",
				QuotationDraftProposal.class,
				QuotationDraftProposal.jsonSchema(mapper),
				AiFeature.STRUCTURED_SMOKE,
				AiGenerationOptions.structuredExtraction(),
				null,
				null);
		StructuredAiResponse<QuotationDraftProposal> result = provider.generateStructured(request);
		assertThat(result.value().customerName()).isEqualTo("Raj Electrical");
		assertThat(result.value().items()).hasSize(4);
		assertThat(usage.getFirst().success()).isTrue();
	}

	@Test
	void malformedJsonFromModel() {
		chatResponse.set("""
				{"model":"qwen3:8b","message":{"role":"assistant","content":"not-json"},"eval_count":1}
				""");
		var request = new StructuredAiRequest<>(
				null,
				"draft",
				QuotationDraftProposal.class,
				QuotationDraftProposal.jsonSchema(mapper),
				AiFeature.STRUCTURED_SMOKE,
				AiGenerationOptions.structuredExtraction(),
				null,
				null);
		assertThatThrownBy(() -> provider.generateStructured(request))
				.isInstanceOf(AiInvalidResponseException.class);
		assertThat(usage.getFirst().success()).isFalse();
	}

	@Test
	void emptyModelResponse() {
		chatResponse.set("""
				{"model":"qwen3:8b","message":{"role":"assistant","content":""}}
				""");
		assertThatThrownBy(() -> provider.generate(AiRequest.of(AiFeature.PROVIDER_SMOKE, "hi")))
				.isInstanceOf(AiInvalidResponseException.class);
	}

	@Test
	void httpErrorMapped() {
		chatStatus.set(500);
		chatResponse.set("{\"error\":\"boom\"}");
		assertThatThrownBy(() -> provider.generate(AiRequest.of(AiFeature.PROVIDER_SMOKE, "hi")))
				.isInstanceOf(AiUnavailableException.class);
	}

	@Test
	void unknownModelHttp404() {
		chatStatus.set(404);
		chatResponse.set("{\"error\":\"model not found\"}");
		assertThatThrownBy(() -> provider.generate(AiRequest.of(AiFeature.PROVIDER_SMOKE, "hi")))
				.isInstanceOf(AiUnavailableException.class)
				.hasMessageContaining("404");
	}

	@Test
	void connectionFailureWhenServerStopped() {
		server.stop(0);
		server = null;
		assertThatThrownBy(() -> provider.generate(AiRequest.of(AiFeature.PROVIDER_SMOKE, "hi")))
				.isInstanceOf(AiUnavailableException.class);
	}

	@Test
	void timeoutMapped() throws Exception {
		server.removeContext("/api/chat");
		server.createContext("/api/chat", exchange -> {
			try {
				Thread.sleep(5_000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, body.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(body);
			}
		});
		assertThatThrownBy(() -> provider.generate(AiRequest.of(AiFeature.PROVIDER_SMOKE, "hi")))
				.isInstanceOfAny(AiTimeoutException.class, AiUnavailableException.class);
	}

	@Test
	void availabilityUsesTagsEndpoint() {
		assertThat(provider.isAvailable()).isTrue();
		tagsStatus.set(500);
		assertThat(provider.isAvailable()).isFalse();
	}
}
