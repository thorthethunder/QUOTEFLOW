package com.quoteflow.payment;

import com.quoteflow.payment.dto.CreatePaymentRequest;
import com.quoteflow.payment.dto.InvoicePaymentsResponse;
import com.quoteflow.payment.dto.PaymentResponse;
import com.quoteflow.payment.dto.VoidPaymentRequest;
import com.quoteflow.payment.pdf.ReceiptPdfService;
import com.quoteflow.security.AuthenticatedUser;
import com.quoteflow.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Tag(name = "Payments")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {

	private final PaymentService paymentService;
	private final ReceiptPdfService receiptPdfService;

	public PaymentController(PaymentService paymentService, ReceiptPdfService receiptPdfService) {
		this.paymentService = paymentService;
		this.receiptPdfService = receiptPdfService;
	}

	@PostMapping("/api/v1/invoices/{invoiceId}/payments")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Record a manual payment against a SENT invoice")
	public PaymentResponse record(
			@PathVariable UUID invoiceId,
			@Valid @RequestBody CreatePaymentRequest request) {
		AuthenticatedUser principal = SecurityUtils.requireCurrentUser();
		return paymentService.record(principal, invoiceId, request);
	}

	@GetMapping("/api/v1/invoices/{invoiceId}/payments")
	@Operation(summary = "List payments and authoritative payment summary for an invoice")
	public InvoicePaymentsResponse list(@PathVariable UUID invoiceId) {
		AuthenticatedUser principal = SecurityUtils.requireCurrentUser();
		return paymentService.listForInvoice(principal, invoiceId);
	}

	@GetMapping("/api/v1/payments/{paymentId}")
	@Operation(summary = "Get payment detail (tenant-scoped)")
	public PaymentResponse get(@PathVariable UUID paymentId) {
		AuthenticatedUser principal = SecurityUtils.requireCurrentUser();
		return paymentService.get(principal, paymentId);
	}

	@PostMapping("/api/v1/payments/{paymentId}/void")
	@Operation(summary = "Void a RECORDED payment (explicit correction; does not delete)")
	public PaymentResponse voidPayment(
			@PathVariable UUID paymentId,
			@RequestBody(required = false) @Valid VoidPaymentRequest request) {
		AuthenticatedUser principal = SecurityUtils.requireCurrentUser();
		return paymentService.voidPayment(principal, paymentId, request != null ? request : new VoidPaymentRequest(null));
	}

	@GetMapping(value = "/api/v1/payments/{paymentId}/receipt.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
	@Operation(summary = "Download authoritative receipt PDF for a payment")
	public ResponseEntity<byte[]> receiptPdf(@PathVariable UUID paymentId) {
		AuthenticatedUser principal = SecurityUtils.requireCurrentUser();
		ReceiptPdfService.GeneratedPdf pdf = receiptPdfService.generate(principal, paymentId);
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + pdf.filename() + "\"")
				.header(HttpHeaders.CACHE_CONTROL, "private, no-store")
				.header(HttpHeaders.PRAGMA, "no-cache")
				.contentType(MediaType.APPLICATION_PDF)
				.body(pdf.content());
	}
}
