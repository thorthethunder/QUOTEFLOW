package com.quoteflow.ai.action.payload;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Canonical stored payload for quotation.createDraft. Immutable after proposal creation.
 */
public record QuotationCreateDraftPayload(
		UUID customerId,
		String customerDisplayName,
		String currency,
		String discountType,
		BigDecimal discountValue,
		BigDecimal taxRate,
		String notes,
		String terms,
		List<LineItem> items
) {
	public record LineItem(String description, BigDecimal quantity, BigDecimal unitPrice) {
	}
}
