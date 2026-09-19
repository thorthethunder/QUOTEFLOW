package com.quoteflow.ai.knowledge;

import java.time.Instant;
import java.util.UUID;

public record KnowledgeDocumentRow(
		UUID id,
		UUID businessId,
		UUID createdByUserId,
		String title,
		KnowledgeSourceType sourceType,
		String originalFilename,
		String contentType,
		KnowledgeDocumentStatus status,
		Instant createdAt,
		Instant updatedAt,
		Instant indexedAt,
		int version
) {
}
