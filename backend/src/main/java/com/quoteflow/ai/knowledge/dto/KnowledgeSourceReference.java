package com.quoteflow.ai.knowledge.dto;

import java.util.UUID;

public record KnowledgeSourceReference(
		UUID documentId,
		UUID chunkId,
		String title,
		int chunkIndex,
		double score,
		String excerpt
) {
}
