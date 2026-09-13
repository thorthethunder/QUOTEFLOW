package com.quoteflow.invoice;

import com.quoteflow.invoice.dto.CreateInvoiceRequest;
import com.quoteflow.invoice.dto.InvoiceResponse;
import com.quoteflow.invoice.dto.PagedInvoiceResponse;
import com.quoteflow.invoice.dto.UpdateInvoiceRequest;
import com.quoteflow.notification.NotificationReferenceType;
import com.quoteflow.notification.NotificationResponse;
import com.quoteflow.notification.NotificationService;
import com.quoteflow.security.AuthenticatedUser;
import com.quoteflow.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/v1/invoices")
@Tag(name = "Invoices")
@SecurityRequirement(name = "bearerAuth")
public class InvoiceController {

	private final InvoiceService invoiceService;
	private final InvoiceReminderService invoiceReminderService;
	private final NotificationService notificationService;

	public InvoiceController(
			InvoiceService invoiceService,
			InvoiceReminderService invoiceReminderService,
			NotificationService notificationService) {
		this.invoiceService = invoiceService;
		this.invoiceReminderService = invoiceReminderService;
		this.notificationService = notificationService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Create a standalone DRAFT invoice with authoritative calculated totals")
	public InvoiceResponse create(@Valid @RequestBody CreateInvoiceRequest request) {
		AuthenticatedUser principal = SecurityUtils.requireCurrentUser();
		return invoiceService.create(principal, request);
	}

	@GetMapping
	@Operation(summary = "List invoices for the authenticated business")
	public PagedInvoiceResponse list(
			@RequestParam(required = false) String q,
			@RequestParam(required = false) InvoiceStatus status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(required = false, defaultValue = "createdAt,desc") String sort) {
		AuthenticatedUser principal = SecurityUtils.requireCurrentUser();
		return invoiceService.list(principal, q, status, page, size, sort);
	}

	@GetMapping("/{invoiceId}")
	@Operation(summary = "Get invoice detail (tenant-scoped)")
	public InvoiceResponse get(@PathVariable UUID invoiceId) {
		AuthenticatedUser principal = SecurityUtils.requireCurrentUser();
		return invoiceService.get(principal, invoiceId);
	}

	@PutMapping("/{invoiceId}")
	@Operation(summary = "Update a DRAFT invoice (optimistic lock via version)")
	public InvoiceResponse update(
			@PathVariable UUID invoiceId,
			@Valid @RequestBody UpdateInvoiceRequest request) {
		AuthenticatedUser principal = SecurityUtils.requireCurrentUser();
		return invoiceService.update(principal, invoiceId, request);
	}

	@PostMapping("/{invoiceId}/send")
	@Operation(summary = "Mark DRAFT invoice as SENT (lifecycle only; no email)")
	public InvoiceResponse send(
			@PathVariable UUID invoiceId,
			@RequestBody(required = false) Map<String, Long> body) {
		AuthenticatedUser principal = SecurityUtils.requireCurrentUser();
		Long version = body != null ? body.get("version") : null;
		return invoiceService.markSent(principal, invoiceId, version);
	}

	@PostMapping("/{invoiceId}/send-reminder")
	@Operation(summary = "Queue payment reminder email for an outstanding SENT invoice")
	public NotificationResponse sendReminder(
			@PathVariable UUID invoiceId,
			@Valid @RequestBody(required = false) SendInvoiceReminderRequest request) {
		AuthenticatedUser principal = SecurityUtils.requireCurrentUser();
		NotificationResponse queued = invoiceReminderService.sendReminder(principal, invoiceId, request);
		invoiceReminderService.dispatchNow(queued.id());
		return notificationService.get(principal.getBusinessId(), queued.id());
	}

	@GetMapping("/{invoiceId}/notifications")
	@Operation(summary = "List recent email notifications for an invoice")
	public List<NotificationResponse> notifications(@PathVariable UUID invoiceId) {
		AuthenticatedUser principal = SecurityUtils.requireCurrentUser();
		invoiceService.get(principal, invoiceId);
		return notificationService.listForReference(
				principal.getBusinessId(),
				NotificationReferenceType.INVOICE,
				invoiceId);
	}

	@PostMapping("/{invoiceId}/cancel")
	@Operation(summary = "Cancel a DRAFT or SENT invoice")
	public InvoiceResponse cancel(
			@PathVariable UUID invoiceId,
			@RequestBody(required = false) Map<String, Long> body) {
		AuthenticatedUser principal = SecurityUtils.requireCurrentUser();
		Long version = body != null ? body.get("version") : null;
		return invoiceService.cancel(principal, invoiceId, version);
	}
}
