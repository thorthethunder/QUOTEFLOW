package com.quoteflow.quotation.pdf;

import com.quoteflow.quotation.Quotation;
import com.quoteflow.quotation.QuotationCalculator;
import com.quoteflow.quotation.QuotationItem;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class QuotationPdfDocumentFactory {

	public QuotationPdfDocument from(Quotation quotation) {
		return from(quotation, quotation.isShowQuoteFlowBranding());
	}

	public QuotationPdfDocument from(Quotation quotation, boolean showQuoteFlowBranding) {
		if (quotation.getBusinessName() == null || quotation.getBusinessName().isBlank()) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Quotation business snapshot is incomplete");
		}
		if (quotation.getCustomerDisplayName() == null || quotation.getCustomerDisplayName().isBlank()) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Quotation customer snapshot is incomplete");
		}
		if (quotation.getItems() == null || quotation.getItems().isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot generate PDF without line items");
		}

		assertPersistedTotalsMatchRecalculation(quotation);

		List<QuotationPdfDocument.LineItem> lines = new ArrayList<>();
		for (QuotationItem item : quotation.getItems()) {
			lines.add(new QuotationPdfDocument.LineItem(
					item.getPosition(),
					item.getDescription(),
					item.getQuantity(),
					item.getUnitPrice(),
					item.getLineSubtotal()));
		}

		return new QuotationPdfDocument(
				quotation.getQuotationNumber(),
				quotation.getStatus(),
				quotation.getIssueDate(),
				quotation.getValidUntil(),
				quotation.getCurrency(),
				new QuotationPdfDocument.BusinessParty(
						quotation.getBusinessName(),
						quotation.getBusinessEmail(),
						quotation.getBusinessPhone(),
						quotation.getBusinessAddressLine1(),
						quotation.getBusinessAddressLine2(),
						quotation.getBusinessCity(),
						quotation.getBusinessStateRegion(),
						quotation.getBusinessPostalCode(),
						quotation.getBusinessCountryCode(),
						quotation.getBusinessTaxId()),
				new QuotationPdfDocument.CustomerParty(
						quotation.getCustomerDisplayName(),
						quotation.getCustomerCompanyName(),
						quotation.getCustomerEmail(),
						quotation.getCustomerPhone(),
						quotation.getCustomerAddressLine1(),
						quotation.getCustomerAddressLine2(),
						quotation.getCustomerCity(),
						quotation.getCustomerStateRegion(),
						quotation.getCustomerPostalCode(),
						quotation.getCustomerCountryCode(),
						quotation.getCustomerTaxId()),
				List.copyOf(lines),
				quotation.getDiscountType(),
				quotation.getDiscountValue(),
				quotation.getTaxRate(),
				quotation.getSubtotal(),
				quotation.getDiscountAmount(),
				quotation.getTaxAmount(),
				quotation.getTotalAmount(),
				quotation.getNotes(),
				quotation.getTerms(),
				showQuoteFlowBranding);
	}

	private static void assertPersistedTotalsMatchRecalculation(Quotation quotation) {
		List<QuotationCalculator.LineInput> inputs = new ArrayList<>();
		for (QuotationItem item : quotation.getItems()) {
			inputs.add(new QuotationCalculator.LineInput(
					item.getPosition(),
					item.getDescription(),
					item.getQuantity(),
					item.getUnitPrice()));
		}
		QuotationCalculator.CalculationResult calc = QuotationCalculator.calculate(
				inputs,
				quotation.getDiscountType(),
				quotation.getDiscountValue(),
				quotation.getTaxRate());

		if (calc.subtotal().compareTo(quotation.getSubtotal()) != 0
				|| calc.discountAmount().compareTo(quotation.getDiscountAmount()) != 0
				|| calc.taxAmount().compareTo(quotation.getTaxAmount()) != 0
				|| calc.totalAmount().compareTo(quotation.getTotalAmount()) != 0) {
			throw new ResponseStatusException(
					HttpStatus.CONFLICT,
					"Quotation financial totals are inconsistent and cannot be rendered");
		}

		for (int i = 0; i < quotation.getItems().size(); i++) {
			QuotationItem item = quotation.getItems().get(i);
			BigDecimal expected = calc.lines().get(i).lineSubtotal();
			if (expected.compareTo(item.getLineSubtotal()) != 0) {
				throw new ResponseStatusException(
						HttpStatus.CONFLICT,
						"Quotation line totals are inconsistent and cannot be rendered");
			}
		}
	}
}
