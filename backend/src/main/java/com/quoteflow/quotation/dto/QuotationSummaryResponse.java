package com.quoteflow.quotation.dto;

import com.quoteflow.quotation.Quotation;
import com.quoteflow.quotation.QuotationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record QuotationSummaryResponse(
		UUID id,
		String quotationNumber,
		String customerDisplayName,
		String customerCompanyName,
		LocalDate issueDate,
		LocalDate validUntil,
		QuotationStatus status,
		String currency,
		BigDecimal totalAmount,
		Instant updatedAt
) {
	public static QuotationSummaryResponse from(Quotation quotation) {
		return new QuotationSummaryResponse(
				quotation.getId(),
				quotation.getQuotationNumber(),
				quotation.getCustomerDisplayName(),
				quotation.getCustomerCompanyName(),
				quotation.getIssueDate(),
				quotation.getValidUntil(),
				quotation.getStatus(),
				quotation.getCurrency(),
				quotation.getTotalAmount(),
				quotation.getUpdatedAt());
	}
}
