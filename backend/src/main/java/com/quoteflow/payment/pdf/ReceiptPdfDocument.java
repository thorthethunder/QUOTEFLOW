package com.quoteflow.payment.pdf;

import com.quoteflow.invoice.Invoice;
import com.quoteflow.payment.Payment;
import com.quoteflow.payment.PaymentRecordStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReceiptPdfDocument(
		String receiptNumber,
		PaymentRecordStatus paymentStatus,
		LocalDate paymentDate,
		String paymentMethod,
		String reference,
		BigDecimal paymentAmount,
		String currency,
		String invoiceNumber,
		BigDecimal invoiceTotal,
		BigDecimal previousPaidAmount,
		BigDecimal remainingBalanceAfterPayment,
		Party business,
		Party customer,
		boolean showQuoteFlowBranding
) {
	public record Party(
			String name,
			String companyName,
			String email,
			String phone,
			String addressLine1,
			String addressLine2,
			String city,
			String stateRegion,
			String postalCode,
			String countryCode,
			String taxId
	) {
	}

	public static ReceiptPdfDocument from(Payment payment, Invoice invoice) {
		return new ReceiptPdfDocument(
				payment.getReceiptNumber(),
				payment.getStatus(),
				payment.getPaymentDate(),
				payment.getPaymentMethod().name(),
				payment.getReference(),
				payment.getAmount(),
				payment.getCurrency(),
				payment.getInvoiceNumberSnapshot(),
				payment.getInvoiceTotalAtPayment(),
				payment.getPreviousPaidAmount(),
				payment.getRemainingBalanceAfterPayment(),
				new Party(
						invoice.getBusinessName(),
						null,
						invoice.getBusinessEmail(),
						invoice.getBusinessPhone(),
						invoice.getBusinessAddressLine1(),
						invoice.getBusinessAddressLine2(),
						invoice.getBusinessCity(),
						invoice.getBusinessStateRegion(),
						invoice.getBusinessPostalCode(),
						invoice.getBusinessCountryCode(),
						invoice.getBusinessTaxId()),
				new Party(
						invoice.getCustomerDisplayName(),
						invoice.getCustomerCompanyName(),
						invoice.getCustomerEmail(),
						invoice.getCustomerPhone(),
						invoice.getCustomerAddressLine1(),
						invoice.getCustomerAddressLine2(),
						invoice.getCustomerCity(),
						invoice.getCustomerStateRegion(),
						invoice.getCustomerPostalCode(),
						invoice.getCustomerCountryCode(),
						invoice.getCustomerTaxId()),
				payment.isShowQuoteFlowBranding());
	}
}
