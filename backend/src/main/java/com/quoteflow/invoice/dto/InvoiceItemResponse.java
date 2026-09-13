package com.quoteflow.invoice.dto;

import com.quoteflow.invoice.InvoiceItem;

import java.math.BigDecimal;
import java.util.UUID;

public record InvoiceItemResponse(
		UUID id,
		int position,
		String description,
		BigDecimal quantity,
		BigDecimal unitPrice,
		BigDecimal lineSubtotal
) {
	public static InvoiceItemResponse from(InvoiceItem item) {
		return new InvoiceItemResponse(
				item.getId(),
				item.getPosition(),
				item.getDescription(),
				item.getQuantity(),
				item.getUnitPrice(),
				item.getLineSubtotal());
	}
}
