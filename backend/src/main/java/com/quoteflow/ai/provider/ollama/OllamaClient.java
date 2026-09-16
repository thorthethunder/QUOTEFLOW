package com.quoteflow.ai.provider.ollama;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.quoteflow.ai.config.AiProperties;
import com.quoteflow.ai.exception.AiException;
import com.quoteflow.ai.exception.AiInvalidResponseException;
import com.quoteflow.ai.exception.AiTimeoutException;
import com.quoteflow.ai.exception.AiUnavailableException;
import com.quoteflow.ai.provider.AiGenerationOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

/**
 * Minimal Ollama HTTP adapter. Only this class speaks Ollama wire format.
 * Base URL is trusted server configuration — never from request payloads.
 */
public class OllamaClient {

	private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);

	private final AiProperties properties;
	private final ObjectMapper objectMapper;
	private final RestClient restClient;

	public OllamaClient(AiProperties properties, ObjectMapper objectMapper) {
		this.properties = properties;
		this.objectMapper = objectMapper;
		Duration connect = properties.getOllama().getConnectTimeout() == null
				? Duration.ofSeconds(5)
				: properties.getOllama().getConnectTimeout();
		Duration read = properties.getOllama().getReadTimeout() == null
				? Duration.ofSeconds(120)
				: properties.getOllama().getReadTimeout();
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(connect)
				.build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
		factory.setReadTimeout(read);
		this.restClient = RestClient.builder()
				.baseUrl(trimSlash(properties.getOllama().getBaseUrl()))
				.requestFactory(factory)
				.build();
	}

	/** Test / advanced construction with an injected RestClient. */
	OllamaClient(AiProperties properties, ObjectMapper objectMapper, RestClient restClient) {
		this.properties = properties;
		this.objectMapper = objectMapper;
		this.restClient = restClient;
	}

	public String model() {
		return properties.getOllama().getModel();
	}

	public boolean isReachable() {
		try {
			restClient.get()
					.uri("/api/tags")
					.retrieve()
					.toBodilessEntity();
			return true;
		} catch (Exception ex) {
			log.debug("ai.ollama.health unavailable reason={}", ex.getClass().getSimpleName());
			return false;
		}
	}

	public boolean hasConfiguredModel() {
		String configured = model();
		if (configured == null || configured.isBlank()) {
			return false;
		}
		try {
			String body = restClient.get()
					.uri("/api/tags")
					.retrieve()
					.body(String.class);
			if (body == null) {
				return false;
			}
			JsonNode root = objectMapper.readTree(body);
			JsonNode models = root.path("models");
			if (!models.isArray()) {
				return false;
			}
			for (JsonNode m : models) {
				String name = m.path("name").asText("");
				if (name.equals(configured) || name.startsWith(configured + ":") || configured.startsWith(name)) {
					return true;
				}
				// Ollama lists "qwen3:8b" exactly; also match tagless
				if (name.equalsIgnoreCase(configured)) {
					return true;
				}
			}
			return false;
		} catch (Exception ex) {
			return false;
		}
	}

	public ChatResult chat(String systemPrompt, String userPrompt, JsonNode formatSchema, AiGenerationOptions options) {
		ObjectNode body = objectMapper.createObjectNode();
		body.put("model", model());
		body.put("stream", false);
		if (options != null && options.disableThinking()) {
			body.put("think", false);
		}
		ArrayNode messages = body.putArray("messages");
		if (systemPrompt != null && !systemPrompt.isBlank()) {
			ObjectNode system = messages.addObject();
			system.put("role", "system");
			system.put("content", systemPrompt);
		}
		ObjectNode user = messages.addObject();
		user.put("role", "user");
		user.put("content", userPrompt);
		if (formatSchema != null) {
			body.set("format", formatSchema);
		}
		ObjectNode opts = body.putObject("options");
		if (options != null && options.temperature() != null) {
			opts.put("temperature", options.temperature());
		}
		if (options != null && options.numPredict() != null) {
			opts.put("num_predict", options.numPredict());
		}

		if (properties.isLogPrompts()) {
			log.debug("ai.ollama.prompt feature_debug length={}", userPrompt.length());
		}

		long start = System.nanoTime();
		try {
			String raw = restClient.post()
					.uri("/api/chat")
					.contentType(MediaType.APPLICATION_JSON)
					.body(body)
					.retrieve()
					.body(String.class);
			long latencyMs = (System.nanoTime() - start) / 1_000_000L;
			return parseChat(raw, latencyMs);
		} catch (RestClientResponseException ex) {
			long latencyMs = (System.nanoTime() - start) / 1_000_000L;
			throw mapHttp(ex, latencyMs);
		} catch (ResourceAccessException ex) {
			long latencyMs = (System.nanoTime() - start) / 1_000_000L;
			throw mapAccess(ex, latencyMs);
		} catch (AiException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new AiUnavailableException("Ollama request failed", ex);
		}
	}

	private ChatResult parseChat(String raw, long latencyMs) {
		if (raw == null || raw.isBlank()) {
			throw new AiInvalidResponseException("Empty Ollama response");
		}
		if (raw.length() > properties.getMaxResponseChars()) {
			throw new AiInvalidResponseException("Ollama response exceeds max size");
		}
		try {
			JsonNode root = objectMapper.readTree(raw);
			String content = root.path("message").path("content").asText(null);
			if (content == null) {
				content = root.path("response").asText(null);
			}
			Integer input = root.has("prompt_eval_count") && root.get("prompt_eval_count").canConvertToInt()
					? root.get("prompt_eval_count").asInt()
					: null;
			Integer output = root.has("eval_count") && root.get("eval_count").canConvertToInt()
					? root.get("eval_count").asInt()
					: null;
			String model = root.path("model").asText(model());
			return new ChatResult(content == null ? "" : content, model, input, output, latencyMs);
		} catch (AiException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new AiInvalidResponseException("Malformed Ollama response", ex);
		}
	}

	private AiException mapHttp(RestClientResponseException ex, long latencyMs) {
		int status = ex.getStatusCode().value();
		if (status == 404) {
			return new AiUnavailableException("Ollama model or endpoint not found (HTTP 404)");
		}
		if (status >= 500) {
			return new AiUnavailableException("Ollama server error HTTP " + status);
		}
		return new AiUnavailableException("Ollama HTTP error " + status);
	}

	private AiException mapAccess(ResourceAccessException ex, long latencyMs) {
		Throwable cause = ex.getMostSpecificCause();
		if (cause instanceof TimeoutException
				|| (cause != null && cause.getClass().getSimpleName().contains("Timeout"))
				|| (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("timed out"))) {
			return new AiTimeoutException("Ollama request timed out", ex);
		}
		return new AiUnavailableException("Ollama unavailable", ex);
	}

	private static String trimSlash(String url) {
		if (url == null || url.isBlank()) {
			return "http://localhost:11434";
		}
		String trimmed = url.trim();
		while (trimmed.endsWith("/")) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed;
	}

	public record ChatResult(
			String content,
			String model,
			Integer inputTokens,
			Integer outputTokens,
			long latencyMs
	) {
	}
}
