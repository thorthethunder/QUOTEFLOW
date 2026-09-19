package com.quoteflow.ai.knowledge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeEmbeddingTest {

	@Test
	void rejectsEmptyWrongDimensionAndNonFiniteVectors() {
		assertThatThrownBy(() -> new KnowledgeEmbedding(List.of(), "HASH", "hash-test", 0))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("required");
		assertThatThrownBy(() -> new KnowledgeEmbedding(List.of(1.0d), "HASH", "hash-test", 2))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("dimension");
		assertThatThrownBy(() -> new KnowledgeEmbedding(List.of(Double.NaN), "HASH", "hash-test", 1))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("finite");
		assertThatThrownBy(() -> new KnowledgeEmbedding(List.of(Double.POSITIVE_INFINITY), "HASH", "hash-test", 1))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("finite");
	}
}
