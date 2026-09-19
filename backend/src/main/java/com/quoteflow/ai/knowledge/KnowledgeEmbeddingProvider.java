package com.quoteflow.ai.knowledge;

public interface KnowledgeEmbeddingProvider {
	String providerName();

	String model();

	int dimension();

	boolean isAvailable();

	KnowledgeEmbedding embed(String text);
}
