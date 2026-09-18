package com.quoteflow.ai.action;

/**
 * Explicit allowlisted AI-assisted action types.
 * Unknown / unlisted types are rejected fail-closed.
 */
public enum AiActionType {
	QUOTATION_CREATE_DRAFT,
	INVOICE_CREATE_DRAFT,
	REMINDER_PREPARE,
	PAYMENT_REMINDER_SEND;

	public String toolName() {
		return switch (this) {
			case QUOTATION_CREATE_DRAFT -> "quotation_create_draft";
			case INVOICE_CREATE_DRAFT -> "invoice_create_draft";
			case REMINDER_PREPARE -> "reminder_prepare";
			case PAYMENT_REMINDER_SEND -> "payment_reminder_send";
		};
	}

	public static AiActionType fromToolName(String name) {
		if (name == null) {
			throw new IllegalArgumentException("Unknown action tool");
		}
		return switch (name) {
			case "quotation_create_draft" -> QUOTATION_CREATE_DRAFT;
			case "invoice_create_draft" -> INVOICE_CREATE_DRAFT;
			case "reminder_prepare" -> REMINDER_PREPARE;
			case "payment_reminder_send" -> PAYMENT_REMINDER_SEND;
			default -> throw new IllegalArgumentException("Unknown action tool: " + name);
		};
	}
}
