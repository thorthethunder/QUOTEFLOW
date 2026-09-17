package com.quoteflow.ai.copilot.dto;

import java.util.UUID;

/**
 * Trusted navigation reference produced by QuoteFlow tools (not by the model inventing URLs).
 */
public record BusinessCopilotReference(
		String type,
		UUID id,
		String displayNumber,
		String label
) {
	public static BusinessCopilotReference customer(UUID id, String name) {
		return new BusinessCopilotReference("CUSTOMER", id, null, name);
	}

	public static BusinessCopilotReference quotation(UUID id, String number) {
		return new BusinessCopilotReference("QUOTATION", id, number, number);
	}

	public static BusinessCopilotReference invoice(UUID id, String number) {
		return new BusinessCopilotReference("INVOICE", id, number, number);
	}

	public static BusinessCopilotReference payment(UUID id, String label) {
		return new BusinessCopilotReference("PAYMENT", id, null, label);
	}
}
