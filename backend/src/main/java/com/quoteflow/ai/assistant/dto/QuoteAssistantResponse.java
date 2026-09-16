package com.quoteflow.ai.assistant.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record QuoteAssistantResponse(
		CustomerMatch customer,
		String proposedCustomerName,
		List<ProposedItem> items,
		String notes,
		String discountType,
		BigDecimal discountValue,
		BigDecimal taxRate,
		List<String> warnings,
		CalculationPreview calculation,
		boolean calculationAvailable
) {

	public record CustomerMatch(
			boolean matched,
			boolean ambiguous,
			UUID id,
			String name,
			List<CustomerCandidate> candidates
	) {
	}

	public record CustomerCandidate(UUID id, String name) {
	}

	public record ProposedItem(
			String description,
			BigDecimal quantity,
			BigDecimal unitPrice,
			boolean needsReview
	) {
	}

	public record CalculationPreview(
			BigDecimal subtotal,
			BigDecimal discountAmount,
			BigDecimal taxAmount,
			BigDecimal total,
			String currency
	) {
	}
}
