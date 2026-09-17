package com.quoteflow.ai.tool;

/**
 * Policy categories for QuoteFlow AI tools.
 * Phase 3+: {@link #READ_ONLY} always eligible.
 * Phase 4+: {@link #ACTION_REQUIRES_APPROVAL} eligible only when AI actions are enabled.
 * {@link #FORBIDDEN} is never registered.
 */
public enum AiToolCategory {
	/** Safe to expose to the model for read queries. */
	READ_ONLY,
	/** Prepare-only actions that create PENDING proposals; never execute directly. */
	ACTION_REQUIRES_APPROVAL,
	/** Never register or execute. */
	FORBIDDEN
}
