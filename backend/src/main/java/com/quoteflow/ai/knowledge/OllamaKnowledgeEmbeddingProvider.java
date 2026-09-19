package com.quoteflow.ai.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.quoteflow.ai.config.AiProperties;
import com.quoteflow.ai.exception.AiUnavailableException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "quoteflow.ai.knowledge", name = "embedding-provider", havingValue = "OLLAMA", matchIfMissing = true)
public class OllamaKnowledgeEmbeddingProvider implements KnowledgeEmbeddingProvider {

	private final AiProperties properties;
	private final RestClient restClient;

	public OllamaKnowledgeEmbeddingProvider(AiProperties properties) {
		this.properties = properties;
		Duration connect = properties.getOllama().getConnectTimeout() == null
				? Duration.ofSeconds(5)
				: properties.getOllama().getConnectTimeout();
		Duration read = properties.getOllama().getReadTimeout() == null
				? Duration.ofSeconds(120)
				: properties.getOllama().getReadTimeout();
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connect).build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
		factory.setReadTimeout(read);
		this.restClient = RestClient.builder()
				.baseUrl(trimSlash(properties.getOllama().getBaseUrl()))
				.requestFactory(factory)
				.build();
	}

	@Override
	public String providerName() {
		return "OLLAMA";
	}

	@Override
	public String model() {
		return properties.getKnowledge().getEmbeddingModel();
	}

	@Override
	public int dimension() {
		return properties.getKnowledge().getEmbeddingDimension();
	}

	@Override
	public boolean isAvailable() {
		try {
			restClient.get().uri("/api/tags").retrieve().toBodilessEntity();
			return true;
		} catch (Exception ex) {
			return false;
		}
	}

	@Override
	public KnowledgeEmbedding embed(String text) {
		try {
			OllamaEmbedResponse response = restClient.post()
					.uri("/api/embed")
					.body(Map.of("model", model(), "input", text == null ? "" : text))
					.retrieve()
					.body(OllamaEmbedResponse.class);
			if (response == null || response.embeddings() == null || response.embeddings().isEmpty()) {
				throw new AiUnavailableException("Embedding provider returned no vector");
			}
			List<Double> vector = response.embeddings().getFirst();
			if (vector.size() != dimension()) {
				throw new AiUnavailableException("Embedding dimension mismatch for configured model");
			}
			return new KnowledgeEmbedding(vector, providerName(), model(), vector.size());
		} catch (AiUnavailableException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new AiUnavailableException("Embedding provider unavailable", ex);
		}
	}

	private static String trimSlash(String url) {
		String trimmed = url == null ? "" : url.trim();
		while (trimmed.endsWith("/")) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record OllamaEmbedResponse(@JsonProperty("embeddings") List<List<Double>> embeddings) {
	}
}
