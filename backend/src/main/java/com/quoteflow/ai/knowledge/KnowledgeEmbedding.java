package com.quoteflow.ai.knowledge;

import java.util.List;

public record KnowledgeEmbedding(
		List<Double> vector,
		String provider,
		String model,
		int dimension
) {
	public KnowledgeEmbedding {
		if (vector == null || vector.isEmpty()) {
			throw new IllegalArgumentException("embedding vector is required");
		}
		if (dimension != vector.size()) {
			throw new IllegalArgumentException("embedding dimension mismatch");
		}
		for (Double value : vector) {
			if (value == null || !Double.isFinite(value)) {
				throw new IllegalArgumentException("embedding vector must contain finite values");
			}
		}
	}
}
