package com.quoteflow.ai.knowledge.dto;

import java.time.Instant;
import java.util.UUID;

public record KnowledgeDocumentDto(
		UUID id,
		String title,
		String sourceType,
		String originalFilename,
		String contentType,
		String status,
		Instant createdAt,
		Instant updatedAt,
		Instant indexedAt,
		int version
) {
}
