package com.quoteflow.quotation;

import com.quoteflow.invoice.InvoiceService;
import com.quoteflow.invoice.dto.InvoiceResponse;
import com.quoteflow.notification.NotificationReferenceType;
import com.quoteflow.notification.NotificationResponse;
import com.quoteflow.notification.NotificationService;
import com.quoteflow.quotation.dto.CreateQuotationRequest;
import com.quoteflow.quotation.dto.PagedQuotationResponse;
import com.quoteflow.quotation.dto.QuotationResponse;
import com.quoteflow.quotation.dto.UpdateQuotationRequest;
import com.quoteflow.quotation.pdf.QuotationPdfService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/quotations")
@Tag(name = "Quotations")
@SecurityRequirement(name = "bearerAuth")
public class QuotationController {

	private final QuotationService quotationService;
	private final QuotationPdfService quotationPdfService;
	private final InvoiceService invoiceService;
	private final QuotationEmailService quotationEmailService;
	private final NotificationService notificationService;

	public QuotationController(
			QuotationService quotationService,
			QuotationPdfService quotationPdfService,
			InvoiceService invoiceService,
			QuotationEmailService quotationEmailService,
			NotificationService notificationService) {
		this.quotationService = quotationService;
		this.quotationPdfService = quotationPdfService;
		this.invoiceService = invoiceService;
		this.quotationEmailService = quotationEmailService;
		this.notificationService = notificationService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Create a DRAFT quotation with authoritative calculated totals")
	public QuotationResponse create(@Valid @RequestBody CreateQuotationRequest request) {
		AuthenticatedUser principal = SecurityUtils.requireCurrentUser();
		return quotationService.create(principal, request);
	}

	@GetMapping
	@Operation(summary = "List quotations for the authenticated business")
	public PagedQuotationResponse list(
			@RequestParam(required = false) String q,
			@RequestParam(required = false) QuotationStatus status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(required = false, defaultValue = "createdAt,desc") String sort) {
		AuthenticatedUser principal = SecurityUtils.requireCurrentUser();
		return quotationService.list(principal, q, status, page, size, sort);
	}

	@GetMapping("/{quotationId}")
	@Operation(summary = "Get quotation detail (tenant-scoped)")
	public QuotationResponse get(@PathVariable UUID quotationId) {
		AuthenticatedUser principal = SecurityUtils.requireCurrentUser();
		return quotationService.get(principal, quotationId);
	}

	@GetMapping(value = "/{quotationId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
	@Operation(summary = "Download authoritative quotation PDF (on-demand, tenant-scoped)")
	public ResponseEntity<byte[]> pdf(@PathVariable UUID quotationId) {
		AuthenticatedUser principal = SecurityUtils.requireCurrentUser();
		QuotationPdfService.GeneratedPdf pdf = quotationPdfService.generate(principal, quotationId);
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + pdf.filename() + "\"")
				.header(HttpHeaders.CACHE_CONTROL, "private, no-store")
				.header(HttpHeaders.PRAGMA, "no-cache")
				.contentType(MediaType.APPLICATION_PDF)
				.body(pdf.content());
	}

	@PutMapping("/{quotationId}")
	@Operation(summary = "Update a DRAFT quotation (optimistic lock via version)")
	public QuotationResponse update(
			@PathVariable UUID quotationId,
			@Valid @RequestBody UpdateQuotationRequest request) {
		AuthenticatedUser principal = SecurityUtils.requireCurrentUser();
		return quotationService.update(principal, quotationId, request);
	}

	@PostMapping("/{quotationId}/send")
	@Operation(summary = "Mark DRAFT quotation as SENT (lifecycle only; no email)")
	public QuotationResponse send(
			@PathVariable UUID quotationId,
			@RequestBody(required = false) Map<String, Long> body) {
		AuthenticatedUser principal = SecurityUtils.requireCurrentUser();
		Long version = body != null ? body.get("version") : null;
		return quotationService.markSent(principal, quotationId, version);
	}

	@PostMapping("/{quotationId}/send-email")
	@Operation(summary = "Queue quotation email (marks DRAFT as SENT first; delivery via outbox)")
	public NotificationResponse sendEmail(
			@PathVariable UUID quotationId,
			@Valid @RequestBody(required = false) SendQuotationEmailRequest request) {
		AuthenticatedUser principal = SecurityUtils.requireCurrentUser();
		NotificationResponse response = quotationEmailService.sendEmail(principal, quotationId, request);
		quotationEmailService.dispatchNow(response.id());
		return notificationService.get(principal.getBusinessId(), response.id());
	}

	@GetMapping("/{quotationId}/notifications")
	@Operation(summary = "List recent email notifications for a quotation")
	public List<NotificationResponse> notifications(@PathVariable UUID quotationId) {
		AuthenticatedUser principal = SecurityUtils.requireCurrentUser();
		quotationService.get(principal, quotationId);
		return notificationService.listForReference(
				principal.getBusinessId(),
				NotificationReferenceType.QUOTATION,
				quotationId);
	}

	@PostMapping("/{quotationId}/cancel")
	@Operation(summary = "Cancel a DRAFT or SENT quotation")
	public QuotationResponse cancel(
			@PathVariable UUID quotationId,
			@RequestBody(required = false) Map<String, Long> body) {
		AuthenticatedUser principal = SecurityUtils.requireCurrentUser();
		Long version = body != null ? body.get("version") : null;
		return quotationService.cancel(principal, quotationId, version);
	}

	@PostMapping("/{quotationId}/convert-to-invoice")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Convert a SENT quotation into a DRAFT invoice (one quotation → one invoice)")
	public InvoiceResponse convertToInvoice(@PathVariable UUID quotationId) {
		AuthenticatedUser principal = SecurityUtils.requireCurrentUser();
		return invoiceService.convertFromQuotation(principal, quotationId);
	}
}
