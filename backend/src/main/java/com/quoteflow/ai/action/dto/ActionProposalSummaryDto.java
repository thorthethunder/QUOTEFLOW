package com.quoteflow.ai.action.dto;

import com.quoteflow.ai.action.AiActionProposalStatus;
import com.quoteflow.ai.action.AiActionType;

import java.time.Instant;
import java.util.UUID;

/**
 * Safe proposal summary for Copilot / approval UI. No payload hash or raw secrets.
 */
public record ActionProposalSummaryDto(
		UUID proposalId,
		AiActionType actionType,
		AiActionProposalStatus status,
		String summary,
		Instant expiresAt,
		String confirmButtonLabel
) {
}
