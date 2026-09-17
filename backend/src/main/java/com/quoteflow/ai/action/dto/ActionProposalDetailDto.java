package com.quoteflow.ai.action.dto;

import com.quoteflow.ai.action.AiActionProposalStatus;
import com.quoteflow.ai.action.AiActionType;

import java.time.Instant;
import java.util.UUID;

/**
 * Full review DTO for GET proposal — includes structured payload + calculator preview.
 * Payload/preview use plain Objects (not JsonNode) so HTTP serialization emits JSON trees,
 * not JsonNode bean introspection properties.
 */
public record ActionProposalDetailDto(
		UUID proposalId,
		AiActionType actionType,
		AiActionProposalStatus status,
		String summary,
		Instant createdAt,
		Instant expiresAt,
		String confirmButtonLabel,
		Object payload,
		Object preview,
		String resultReferenceType,
		UUID resultReferenceId
) {
}
