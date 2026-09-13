package com.quoteflow.quotation.dto;

import com.quoteflow.quotation.QuotationItem;

import java.math.BigDecimal;
import java.util.UUID;

public record QuotationItemResponse(
		UUID id,
		int position,
		String description,
		BigDecimal quantity,
		BigDecimal unitPrice,
		BigDecimal lineSubtotal
) {
	public static QuotationItemResponse from(QuotationItem item) {
		return new QuotationItemResponse(
				item.getId(),
				item.getPosition(),
				item.getDescription(),
				item.getQuantity(),
				item.getUnitPrice(),
				item.getLineSubtotal());
	}
}
