package com.quoteflow.ai.knowledge;

import com.quoteflow.ai.config.AiProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "quoteflow.ai.knowledge", name = "embedding-provider", havingValue = "HASH")
public class HashKnowledgeEmbeddingProvider implements KnowledgeEmbeddingProvider {

	private final AiProperties properties;

	public HashKnowledgeEmbeddingProvider(AiProperties properties) {
		this.properties = properties;
	}

	@Override
	public String providerName() {
		return "HASH";
	}

	@Override
	public String model() {
		return properties.getKnowledge().getEmbeddingModel().isBlank()
				? "hash-dev"
				: properties.getKnowledge().getEmbeddingModel();
	}

	@Override
	public int dimension() {
		return properties.getKnowledge().getEmbeddingDimension();
	}

	@Override
	public boolean isAvailable() {
		return true;
	}

	@Override
	public KnowledgeEmbedding embed(String text) {
		int dim = dimension();
		double[] values = new double[dim];
		String normalized = text == null ? "" : text.toLowerCase();
		for (String token : normalized.split("[^a-z0-9]+")) {
			if (token.isBlank()) {
				continue;
			}
			byte[] digest = sha256(token);
			int idx = Math.floorMod(((digest[0] & 0xff) << 8) | (digest[1] & 0xff), dim);
			values[idx] += 1.0d;
		}
		double norm = 0.0d;
		for (double v : values) {
			norm += v * v;
		}
		norm = Math.sqrt(norm);
		List<Double> out = new ArrayList<>(dim);
		for (double v : values) {
			out.add(norm == 0.0d ? 0.0d : v / norm);
		}
		return new KnowledgeEmbedding(out, providerName(), model(), dim);
	}

	private static byte[] sha256(String value) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 unavailable", ex);
		}
	}
}
