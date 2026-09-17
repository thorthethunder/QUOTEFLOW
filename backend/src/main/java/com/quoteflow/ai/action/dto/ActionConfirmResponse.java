package com.quoteflow.ai.action.dto;

import java.util.UUID;

public record ActionConfirmResponse(
		UUID proposalId,
		String status,
		String resultReferenceType,
		UUID resultReferenceId,
		String message
) {
}
