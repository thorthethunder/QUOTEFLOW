package com.quoteflow.ai.tool;

/**
 * Policy categories for QuoteFlow AI tools.
 * Phase 3 registers and executes only {@link #READ_ONLY}.
 */
public enum AiToolCategory {
	/** Safe to expose to the model for this phase. */
	READ_ONLY,
	/** Documented for Phase 4+; must not be registered yet. */
	ACTION_REQUIRES_APPROVAL,
	/** Never register or execute. */
	FORBIDDEN
}
