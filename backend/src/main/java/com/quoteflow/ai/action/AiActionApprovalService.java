package com.quoteflow.ai.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quoteflow.ai.action.dto.ActionConfirmResponse;
import com.quoteflow.ai.action.dto.ActionProposalDetailDto;
import com.quoteflow.ai.action.dto.ActionProposalSummaryDto;
import com.quoteflow.ai.action.payload.InvoiceCreateDraftPayload;
import com.quoteflow.ai.action.payload.QuotationCreateDraftPayload;
import com.quoteflow.ai.action.payload.ReminderPreparePayload;
import com.quoteflow.ai.config.AiProperties;
import com.quoteflow.common.api.DomainApiException;
import com.quoteflow.customer.CustomerStatus;
import com.quoteflow.customer.CustomerService;
import com.quoteflow.customer.dto.CustomerResponse;
import com.quoteflow.finance.DiscountType;
import com.quoteflow.finance.FinancialDocumentCalculator;
import com.quoteflow.invoice.InvoiceService;
import com.quoteflow.invoice.InvoiceStatus;
import com.quoteflow.invoice.dto.CreateInvoiceRequest;
import com.quoteflow.invoice.dto.InvoiceItemRequest;
import com.quoteflow.invoice.dto.InvoiceResponse;
import com.quoteflow.payment.dto.PaymentSummaryResponse;
import com.quoteflow.quotation.QuotationService;
import com.quoteflow.quotation.dto.CreateQuotationRequest;
import com.quoteflow.quotation.dto.QuotationItemRequest;
import com.quoteflow.quotation.dto.QuotationResponse;
import com.quoteflow.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class AiActionApprovalService {

	private final AiActionProposalRepository proposalRepository;
	private final AiActionIntegrityService integrityService;
	private final AiActionAuditRecorder auditRecorder;
	private final AiProperties aiProperties;
	private final ObjectMapper objectMapper;
	private final QuotationService quotationService;
	private final InvoiceService invoiceService;
	private final CustomerService customerService;
	private final TransactionTemplate transactionTemplate;

	public AiActionApprovalService(
			AiActionProposalRepository proposalRepository,
			AiActionIntegrityService integrityService,
			AiActionAuditRecorder auditRecorder,
			AiProperties aiProperties,
			ObjectMapper objectMapper,
			QuotationService quotationService,
			InvoiceService invoiceService,
			CustomerService customerService,
			TransactionTemplate transactionTemplate) {
		this.proposalRepository = proposalRepository;
		this.integrityService = integrityService;
		this.auditRecorder = auditRecorder;
		this.aiProperties = aiProperties;
		this.objectMapper = objectMapper;
		this.quotationService = quotationService;
		this.invoiceService = invoiceService;
		this.customerService = customerService;
		this.transactionTemplate = transactionTemplate;
	}

	public void requireActionsEnabled() {
		if (!aiProperties.getActions().isEnabled()) {
			throw new DomainApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_ACTIONS_DISABLED",
					"AI-assisted actions are disabled");
		}
	}

	@Transactional
	public ActionProposalSummaryDto prepareQuotationDraft(
			AuthenticatedUser principal, QuotationCreateDraftPayload draft) {
		requireActionsEnabled();
		CustomerResponse customer = customerService.get(principal, draft.customerId());
		if (customer.status() != CustomerStatus.ACTIVE) {
			throw new DomainApiException(HttpStatus.CONFLICT, "CUSTOMER_NOT_ACTIVE",
					"Customer is not active");
		}
		QuotationCreateDraftPayload canonical = new QuotationCreateDraftPayload(
				customer.id(),
				customer.displayName(),
				normalizeCurrency(draft.currency()),
				normalizeDiscount(draft.discountType()),
				nz(draft.discountValue()),
				nz(draft.taxRate()),
				blankToNull(draft.notes()),
				blankToNull(draft.terms()),
				normalizeLines(draft.items()));
		Map<String, Object> preview = calculatePreview(canonical.items(), canonical.discountType(),
				canonical.discountValue(), canonical.taxRate(), canonical.currency());
		return persist(
				principal,
				AiActionType.QUOTATION_CREATE_DRAFT,
				canonical,
				"Create draft quotation for " + customer.displayName(),
				preview);
	}

	@Transactional
	public ActionProposalSummaryDto prepareInvoiceDraft(
			AuthenticatedUser principal, InvoiceCreateDraftPayload draft) {
		requireActionsEnabled();
		CustomerResponse customer = customerService.get(principal, draft.customerId());
		if (customer.status() != CustomerStatus.ACTIVE) {
			throw new DomainApiException(HttpStatus.CONFLICT, "CUSTOMER_NOT_ACTIVE",
					"Customer is not active");
		}
		InvoiceCreateDraftPayload canonical = new InvoiceCreateDraftPayload(
				customer.id(),
				customer.displayName(),
				normalizeCurrency(draft.currency()),
				normalizeDiscount(draft.discountType()),
				nz(draft.discountValue()),
				nz(draft.taxRate()),
				blankToNull(draft.notes()),
				blankToNull(draft.terms()),
				normalizeInvoiceLines(draft.items()));
		Map<String, Object> preview = calculatePreview(toGenericLines(canonical.items()), canonical.discountType(),
				canonical.discountValue(), canonical.taxRate(), canonical.currency());
		return persist(
				principal,
				AiActionType.INVOICE_CREATE_DRAFT,
				canonical,
				"Create draft invoice for " + customer.displayName(),
				preview);
	}

	@Transactional
	public ActionProposalSummaryDto prepareReminder(
			AuthenticatedUser principal, UUID invoiceId, String subject, String bodyPlainText) {
		requireActionsEnabled();
		InvoiceResponse invoice = invoiceService.get(principal, invoiceId);
		if (invoice.status() != InvoiceStatus.SENT) {
			throw new DomainApiException(HttpStatus.CONFLICT, "INVALID_STATUS",
					"Only sent invoices can have reminders prepared");
		}
		PaymentSummaryResponse summary = invoice.paymentSummary();
		if (summary == null || summary.balanceDue() == null || summary.balanceDue().compareTo(BigDecimal.ZERO) <= 0) {
			throw new DomainApiException(HttpStatus.CONFLICT, "INVOICE_NOT_OUTSTANDING",
					"Invoice has no outstanding balance");
		}
		String safeSubject = boundText(subject, 200, "Payment reminder for " + invoice.invoiceNumber());
		String safeBody = boundText(bodyPlainText, 4000, "");
		if (!StringUtils.hasText(safeBody)) {
			throw new DomainApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
					"Reminder body is required");
		}
		ReminderPreparePayload payload = new ReminderPreparePayload(
				invoice.id(),
				invoice.invoiceNumber(),
				invoice.customerDisplayName(),
				invoice.currency(),
				summary.balanceDue(),
				invoice.dueDate() == null ? null : invoice.dueDate().toString(),
				safeSubject,
				safeBody);
		Map<String, Object> preview = new LinkedHashMap<>();
		preview.put("invoiceNumber", invoice.invoiceNumber());
		preview.put("balanceDue", summary.balanceDue());
		preview.put("currency", invoice.currency());
		preview.put("sendsEmail", false);
		preview.put("note", "Confirmation accepts this prepared reminder text only. Email was not sent in Phase 4.");
		return persist(
				principal,
				AiActionType.REMINDER_PREPARE,
				payload,
				"Prepare reminder for " + invoice.invoiceNumber() + " (review only — not sent)",
				preview);
	}

	@Transactional
	public ActionProposalDetailDto get(AuthenticatedUser principal, UUID proposalId) {
		AiActionProposal proposal = loadVisible(principal, proposalId);
		expireIfNeeded(proposal);
		return toDetail(proposal);
	}

	/**
	 * Confirm is a two-phase call so EXPIRED can be persisted without self-deadlocking:
	 * FOR UPDATE in the transactional phase must not nest {@code REQUIRES_NEW} updates
	 * on the same row. Mark expired in-transaction, commit via TransactionTemplate, then throw.
	 */
	public ActionConfirmResponse confirm(AuthenticatedUser principal, UUID proposalId) {
		if (!aiProperties.getActions().isEnabled()) {
			throw new DomainApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_ACTIONS_DISABLED",
					"AI-assisted actions are disabled");
		}
		ConfirmOutcome outcome = transactionTemplate.execute(status -> confirmInTransaction(principal, proposalId));
		if (outcome == null || outcome.expired()) {
			throw new DomainApiException(HttpStatus.CONFLICT, "AI_ACTION_EXPIRED",
					"This action can no longer be completed with the reviewed details. Please prepare it again.");
		}
		return outcome.response();
	}

	private ConfirmOutcome confirmInTransaction(AuthenticatedUser principal, UUID proposalId) {
		AiActionProposal proposal = proposalRepository
				.findByIdAndBusinessIdForUpdate(proposalId, principal.getBusinessId())
				.orElseThrow(() -> notFound());

		if (!proposal.getRequestedByUserId().equals(principal.getUserId())) {
			throw notFound();
		}

		Instant now = Instant.now();
		if (proposal.isExpired(now)) {
			proposal.markExpired(now);
			proposalRepository.save(proposal);
			auditRecorder.record("EXPIRED", proposal.getId(), proposal.getActionType(),
					principal.getBusinessId(), principal.getUserId(), "confirm_rejected");
			return ConfirmOutcome.ofExpired();
		}
		if (proposal.getStatus() == AiActionProposalStatus.EXECUTED) {
			throw new DomainApiException(HttpStatus.CONFLICT, "AI_ACTION_ALREADY_EXECUTED",
					"This action was already confirmed.");
		}
		if (proposal.getStatus() == AiActionProposalStatus.CANCELLED) {
			throw new DomainApiException(HttpStatus.CONFLICT, "AI_ACTION_CANCELLED",
					"This action was cancelled.");
		}
		if (proposal.getStatus() != AiActionProposalStatus.PENDING) {
			throw new DomainApiException(HttpStatus.CONFLICT, "AI_ACTION_NOT_PENDING",
					"This action can no longer be completed with the reviewed details. Please prepare it again.");
		}
		if (!integrityService.verifyStored(proposal)) {
			auditRecorder.record("INTEGRITY_FAIL", proposal.getId(), proposal.getActionType(),
					principal.getBusinessId(), principal.getUserId(), null);
			throw new DomainApiException(HttpStatus.CONFLICT, "AI_ACTION_INTEGRITY",
					"This action can no longer be completed with the reviewed details. Please prepare it again.");
		}

		try {
			ExecutionResult result = execute(principal, proposal);
			proposal.markExecuted(now, result.type(), result.id());
			proposalRepository.save(proposal);
			auditRecorder.record("CONFIRMED_EXECUTED", proposal.getId(), proposal.getActionType(),
					principal.getBusinessId(), principal.getUserId(), result.type());
			return ConfirmOutcome.ok(new ActionConfirmResponse(
					proposal.getId(),
					AiActionProposalStatus.EXECUTED.name(),
					result.type(),
					result.id(),
					result.message()));
		} catch (DomainApiException ex) {
			auditRecorder.record("CONFIRM_FAILED", proposal.getId(), proposal.getActionType(),
					principal.getBusinessId(), principal.getUserId(), ex.getCode());
			throw new DomainApiException(HttpStatus.CONFLICT, "AI_ACTION_REVALIDATION",
					"This action can no longer be completed with the reviewed details. Please prepare it again.");
		} catch (org.springframework.web.server.ResponseStatusException ex) {
			auditRecorder.record("CONFIRM_FAILED", proposal.getId(), proposal.getActionType(),
					principal.getBusinessId(), principal.getUserId(), String.valueOf(ex.getStatusCode().value()));
			throw new DomainApiException(HttpStatus.CONFLICT, "AI_ACTION_REVALIDATION",
					"This action can no longer be completed with the reviewed details. Please prepare it again.");
		} catch (ObjectOptimisticLockingFailureException ex) {
			throw new DomainApiException(HttpStatus.CONFLICT, "AI_ACTION_CONFLICT",
					"This action was already confirmed.");
		}
	}

	@Transactional
	public ActionProposalDetailDto cancel(AuthenticatedUser principal, UUID proposalId) {
		AiActionProposal proposal = proposalRepository
				.findByIdAndBusinessIdForUpdate(proposalId, principal.getBusinessId())
				.orElseThrow(() -> notFound());
		if (!proposal.getRequestedByUserId().equals(principal.getUserId())) {
			throw notFound();
		}
		Instant now = Instant.now();
		if (proposal.isExpired(now)) {
			proposal.markExpired(now);
			proposalRepository.save(proposal);
			return toDetail(proposal);
		}
		if (proposal.getStatus() != AiActionProposalStatus.PENDING) {
			throw new DomainApiException(HttpStatus.CONFLICT, "AI_ACTION_NOT_PENDING",
					"Only pending actions can be cancelled.");
		}
		proposal.markCancelled(now);
		proposalRepository.save(proposal);
		auditRecorder.record("CANCELLED", proposal.getId(), proposal.getActionType(),
				principal.getBusinessId(), principal.getUserId(), null);
		return toDetail(proposal);
	}

	private ExecutionResult execute(AuthenticatedUser principal, AiActionProposal proposal) {
		return switch (proposal.getActionType()) {
			case QUOTATION_CREATE_DRAFT -> {
				QuotationCreateDraftPayload payload = read(proposal, QuotationCreateDraftPayload.class);
				CreateQuotationRequest request = new CreateQuotationRequest(
						payload.customerId(),
						null,
						null,
						payload.currency(),
						com.quoteflow.quotation.DiscountType.valueOf(payload.discountType()),
						payload.discountValue(),
						payload.taxRate(),
						payload.notes(),
						payload.terms(),
						payload.items().stream()
								.map(i -> new QuotationItemRequest(i.description(), i.quantity(), i.unitPrice()))
								.toList(),
						null);
				QuotationResponse created = quotationService.create(principal, request);
				yield new ExecutionResult("QUOTATION", created.id(),
						"Draft quotation " + created.quotationNumber() + " created.");
			}
			case INVOICE_CREATE_DRAFT -> {
				InvoiceCreateDraftPayload payload = read(proposal, InvoiceCreateDraftPayload.class);
				CreateInvoiceRequest request = new CreateInvoiceRequest(
						payload.customerId(),
						null,
						null,
						payload.currency(),
						DiscountType.valueOf(payload.discountType()),
						payload.discountValue(),
						payload.taxRate(),
						payload.notes(),
						payload.terms(),
						payload.items().stream()
								.map(i -> new InvoiceItemRequest(i.description(), i.quantity(), i.unitPrice()))
								.toList());
				InvoiceResponse created = invoiceService.create(principal, request);
				yield new ExecutionResult("INVOICE", created.id(),
						"Draft invoice " + created.invoiceNumber() + " created.");
			}
			case REMINDER_PREPARE -> {
				ReminderPreparePayload payload = read(proposal, ReminderPreparePayload.class);
				// Phase 4: accept prepared reminder only — no email send.
				yield new ExecutionResult("REMINDER_PREPARED", payload.invoiceId(),
						"Prepared reminder accepted for " + payload.invoiceNumber()
								+ ". Email was not sent.");
			}
		};
	}

	private ActionProposalSummaryDto persist(
			AuthenticatedUser principal,
			AiActionType type,
			Object payload,
			String summary,
			Map<String, Object> preview) {
		String canonical = integrityService.toCanonicalJson(payload);
		if (canonical.length() > AiProperties.Actions.HARD_MAX_PAYLOAD_CHARS) {
			throw new DomainApiException(HttpStatus.BAD_REQUEST, "AI_ACTION_PAYLOAD_TOO_LARGE",
					"Action payload exceeds maximum size");
		}
		String hash = integrityService.hash(type, principal.getBusinessId(), principal.getUserId(), canonical);
		Instant now = Instant.now();
		Instant expires = now.plus(aiProperties.getActions().getApprovalTtl());
		String previewJson;
		try {
			previewJson = objectMapper.writeValueAsString(preview == null ? Map.of() : preview);
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to serialize action preview", ex);
		}
		AiActionProposal proposal = new AiActionProposal(
				principal.getBusinessId(),
				principal.getUserId(),
				type,
				canonical,
				hash,
				truncate(summary, 500),
				previewJson,
				now,
				expires);
		proposalRepository.save(proposal);
		auditRecorder.record("CREATED", proposal.getId(), type, principal.getBusinessId(), principal.getUserId(), null);
		return toSummary(proposal);
	}

	private AiActionProposal loadVisible(AuthenticatedUser principal, UUID proposalId) {
		AiActionProposal proposal = proposalRepository
				.findByIdAndBusinessId(proposalId, principal.getBusinessId())
				.orElseThrow(() -> notFound());
		if (!proposal.getRequestedByUserId().equals(principal.getUserId())) {
			throw notFound();
		}
		return proposal;
	}

	private void expireIfNeeded(AiActionProposal proposal) {
		Instant now = Instant.now();
		if (proposal.isExpired(now) && proposal.getStatus() == AiActionProposalStatus.PENDING) {
			// Same-TX update (no FOR UPDATE held here) — avoids REQUIRES_NEW self-deadlock.
			proposal.markExpired(now);
			proposalRepository.save(proposal);
			auditRecorder.record("EXPIRED", proposal.getId(), proposal.getActionType(),
					proposal.getBusinessId(), proposal.getRequestedByUserId(), "lazy");
		}
	}

	private ActionProposalDetailDto toDetail(AiActionProposal proposal) {
		try {
			Object payload = objectMapper.readValue(proposal.getPayloadJson(), Object.class);
			Object preview = proposal.getPreviewJson() == null
					? Map.of()
					: objectMapper.readValue(proposal.getPreviewJson(), Object.class);
			return new ActionProposalDetailDto(
					proposal.getId(),
					proposal.getActionType(),
					proposal.getStatus(),
					proposal.getSummary(),
					proposal.getCreatedAt(),
					proposal.getExpiresAt(),
					confirmLabel(proposal.getActionType()),
					payload,
					preview,
					proposal.getResultReferenceType(),
					proposal.getResultReferenceId());
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to parse proposal", ex);
		}
	}

	public static ActionProposalSummaryDto toSummary(AiActionProposal proposal) {
		return new ActionProposalSummaryDto(
				proposal.getId(),
				proposal.getActionType(),
				proposal.getStatus(),
				proposal.getSummary(),
				proposal.getExpiresAt(),
				confirmLabel(proposal.getActionType()));
	}

	static String confirmLabel(AiActionType type) {
		return switch (type) {
			case QUOTATION_CREATE_DRAFT -> "Create Draft Quotation";
			case INVOICE_CREATE_DRAFT -> "Create Draft Invoice";
			case REMINDER_PREPARE -> "Accept Prepared Reminder";
		};
	}

	private <T> T read(AiActionProposal proposal, Class<T> type) {
		try {
			return objectMapper.readValue(proposal.getPayloadJson(), type);
		} catch (Exception ex) {
			throw new DomainApiException(HttpStatus.CONFLICT, "AI_ACTION_INTEGRITY",
					"This action can no longer be completed with the reviewed details. Please prepare it again.");
		}
	}

	private Map<String, Object> calculatePreview(
			List<QuotationCreateDraftPayload.LineItem> items,
			String discountType,
			BigDecimal discountValue,
			BigDecimal taxRate,
			String currency) {
		List<FinancialDocumentCalculator.LineInput> lines = new ArrayList<>();
		int pos = 1;
		for (QuotationCreateDraftPayload.LineItem item : items) {
			lines.add(new FinancialDocumentCalculator.LineInput(
					pos++, item.description(), item.quantity(), item.unitPrice()));
		}
		var calc = FinancialDocumentCalculator.calculate(
				lines,
				DiscountType.valueOf(discountType),
				discountValue,
				taxRate);
		Map<String, Object> preview = new LinkedHashMap<>();
		preview.put("currency", currency);
		preview.put("subtotal", calc.subtotal().toPlainString());
		preview.put("discountAmount", calc.discountAmount().toPlainString());
		preview.put("taxAmount", calc.taxAmount().toPlainString());
		preview.put("total", calc.totalAmount().toPlainString());
		preview.put("authoritative", true);
		return preview;
	}

	private List<QuotationCreateDraftPayload.LineItem> toGenericLines(List<InvoiceCreateDraftPayload.LineItem> items) {
		return items.stream()
				.map(i -> new QuotationCreateDraftPayload.LineItem(i.description(), i.quantity(), i.unitPrice()))
				.toList();
	}

	private List<QuotationCreateDraftPayload.LineItem> normalizeLines(List<QuotationCreateDraftPayload.LineItem> items) {
		if (items == null || items.isEmpty()) {
			throw new DomainApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "At least one line item is required");
		}
		int max = AiProperties.Actions.HARD_MAX_ITEMS;
		if (items.size() > max) {
			throw new DomainApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
					"Too many line items (max " + max + ")");
		}
		List<QuotationCreateDraftPayload.LineItem> out = new ArrayList<>();
		for (QuotationCreateDraftPayload.LineItem item : items) {
			if (item == null || !StringUtils.hasText(item.description())
					|| item.quantity() == null || item.unitPrice() == null) {
				throw new DomainApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid line item");
			}
			out.add(new QuotationCreateDraftPayload.LineItem(
					item.description().trim(),
					item.quantity(),
					item.unitPrice()));
		}
		return List.copyOf(out);
	}

	private List<InvoiceCreateDraftPayload.LineItem> normalizeInvoiceLines(List<InvoiceCreateDraftPayload.LineItem> items) {
		return normalizeLines(items == null ? List.of() : items.stream()
				.map(i -> new QuotationCreateDraftPayload.LineItem(
						i == null ? null : i.description(),
						i == null ? null : i.quantity(),
						i == null ? null : i.unitPrice()))
				.toList()).stream()
				.map(i -> new InvoiceCreateDraftPayload.LineItem(i.description(), i.quantity(), i.unitPrice()))
				.toList();
	}

	private static String normalizeCurrency(String currency) {
		if (!StringUtils.hasText(currency)) {
			return "INR";
		}
		String c = currency.trim().toUpperCase(Locale.ROOT);
		if (!c.matches("^[A-Z]{3}$")) {
			throw new DomainApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid currency");
		}
		return c;
	}

	private static String normalizeDiscount(String discountType) {
		if (!StringUtils.hasText(discountType)) {
			return DiscountType.NONE.name();
		}
		try {
			return DiscountType.valueOf(discountType.trim().toUpperCase(Locale.ROOT)).name();
		} catch (Exception ex) {
			throw new DomainApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid discountType");
		}
	}

	private static BigDecimal nz(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}

	private static String blankToNull(String value) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		return value.trim();
	}

	private static String boundText(String value, int max, String fallback) {
		String v = StringUtils.hasText(value) ? value.trim() : fallback;
		if (v.length() > max) {
			return v.substring(0, max);
		}
		return v;
	}

	private static String truncate(String value, int max) {
		if (value == null) {
			return "";
		}
		return value.length() <= max ? value : value.substring(0, max);
	}

	private static DomainApiException notFound() {
		return new DomainApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Action proposal not found");
	}

	private record ExecutionResult(String type, UUID id, String message) {
	}

	private record ConfirmOutcome(boolean expired, ActionConfirmResponse response) {
		static ConfirmOutcome ofExpired() {
			return new ConfirmOutcome(true, null);
		}

		static ConfirmOutcome ok(ActionConfirmResponse response) {
			return new ConfirmOutcome(false, response);
		}
	}
}
