package com.quoteflow.ai.knowledge;

import java.util.UUID;

public record KnowledgeSearchResult(
		UUID chunkId,
		UUID documentId,
		String title,
		int chunkIndex,
		String text,
		double score
) {
}
